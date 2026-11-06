using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Threading;
using NAudio.CoreAudioApi;
using NAudio.Wave;

// SYSTEM output mix only. No microphone, files, network, Spotify API or process injection.
internal static class Program
{
    private const int Magic = 0x53524d32;
    private sealed class Frame
    {
        public readonly float[] Samples = new float[4096];
        public int Count, Rate;
        public long Captured;
    }
    private static readonly object Gate = new object();
    private static readonly Queue<Frame> Ready = new Queue<Frame>();
    private static readonly Stack<Frame> Free = new Stack<Frame>();
    private static readonly AutoResetEvent Signal = new AutoResetEvent(false);
    private static volatile bool stopping, recordingStopped;
    private static int channels, bits, rate;
    private static bool ieee;
    private static readonly Guid FloatFormat = new Guid("00000003-0000-0010-8000-00aa00389b71");
    private static readonly Guid PcmFormat = new Guid("00000001-0000-0010-8000-00aa00389b71");
    private sealed class UnsupportedFormat : Exception { }

    private static int Main(string[] args)
    {
        if (args.Length > 0 && args[0] == "--self-test") return SelfTest();
        for (int i = 0; i < 8; i++) Free.Push(new Frame());
        var input = new Thread(() => { try { Console.ReadLine(); } catch { } stopping = true; Signal.Set(); });
        input.IsBackground = true; input.Start();
        using (var output = new BinaryWriter(new BufferedStream(Console.OpenStandardOutput(), 32768)))
        {
            while (!stopping)
            {
                try
                {
                    using (var enumerator = new MMDeviceEnumerator())
                    using (var device = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia))
                    using (var capture = new WasapiLoopbackCapture(device))
                    {
                        string deviceId = device.ID;
                        WaveFormat format = capture.WaveFormat;
                        channels = format.Channels; bits = format.BitsPerSample; rate = format.SampleRate;
                        var extended = format as WaveFormatExtensible;
                        ieee = format.Encoding == WaveFormatEncoding.IeeeFloat || (extended != null && extended.SubFormat == FloatFormat);
                        bool pcm = format.Encoding == WaveFormatEncoding.Pcm || (extended != null && extended.SubFormat == PcmFormat);
                        if ((!pcm && !ieee) || channels < 1 || channels > 32 || rate < 8000 || rate > 384000 ||
                            (!ieee && bits != 16 && bits != 24 && bits != 32) || (ieee && bits != 32))
                            throw new UnsupportedFormat();
                        recordingStopped = false;
                        capture.DataAvailable += OnData;
                        capture.RecordingStopped += (sender, eventArgs) => { recordingStopped = true; Signal.Set(); };
                        capture.StartRecording(); Status(output, 1);
                        long checkedAt = Stopwatch.GetTimestamp();
                        while (!stopping && !recordingStopped)
                        {
                            Signal.WaitOne(20);
                            Drain(output);
                            if ((Stopwatch.GetTimestamp() - checkedAt) / (double)Stopwatch.Frequency >= 1)
                            {
                                checkedAt = Stopwatch.GetTimestamp();
                                using (var next = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia))
                                {
                                    if (next.ID != deviceId) { Status(output, 3); break; }
                                }
                            }
                        }
                        if (recordingStopped && !stopping) Status(output, 5);
                        capture.StopRecording();
                        capture.DataAvailable -= OnData;
                    }
                    if (!stopping) Thread.Sleep(250);
                }
                catch (IOException) { stopping = true; }
                catch (UnsupportedFormat) { try { Status(output, 4); } catch { stopping = true; } if (!stopping) Signal.WaitOne(1000); }
                catch
                {
                    try { Status(output, 2); } catch { stopping = true; }
                    if (!stopping) Signal.WaitOne(1000);
                }
                finally { ClearQueue(); }
            }
        }
        return 0;
    }
    private static void OnData(object sender, WaveInEventArgs e)
    {
        int bytes = bits / 8;
        int total = e.BytesRecorded / (bytes * channels), offset = 0;
        long callback = Stopwatch.GetTimestamp();
        while (!stopping && offset < total)
        {
            Frame frame;
            lock (Gate) { if (Free.Count == 0) return; frame = Free.Pop(); }
            frame.Count = Math.Min(frame.Samples.Length, total - offset); frame.Rate = rate;
            double secondsBeforeCallback = (total - offset - frame.Count) / (double)rate;
            frame.Captured = callback - (long)(secondsBeforeCallback * Stopwatch.Frequency);
            for (int i = 0; i < frame.Count; i++)
            {
                double mono = 0;
                for (int channel = 0; channel < channels; channel++)
                    mono += Decode(e.Buffer, ((offset + i) * channels + channel) * bytes, bits, ieee);
                frame.Samples[i] = (float)Math.Max(-1, Math.Min(1, mono / channels));
            }
            lock (Gate) Ready.Enqueue(frame);
            offset += frame.Count; Signal.Set();
        }
    }
    internal static float Decode(byte[] data, int offset, int bits, bool floating)
    {
        if (floating) { float value = BitConverter.ToSingle(data, offset); return float.IsNaN(value) || float.IsInfinity(value) ? 0 : value; }
        if (bits == 16) return BitConverter.ToInt16(data, offset) / 32768f;
        if (bits == 24)
        {
            int value = data[offset] | (data[offset + 1] << 8) | (data[offset + 2] << 16);
            if ((value & 0x800000) != 0) value |= unchecked((int)0xff000000);
            return value / 8388608f;
        }
        return BitConverter.ToInt32(data, offset) / 2147483648f;
    }
    private static void Drain(BinaryWriter output)
    {
        while (true)
        {
            Frame frame;
            lock (Gate) { if (Ready.Count == 0) return; frame = Ready.Dequeue(); }
            try
            {
                long writtenNs = (long)(Stopwatch.GetTimestamp() * (1000000000.0 / Stopwatch.Frequency));
                long capturedNs = (long)(frame.Captured * (1000000000.0 / Stopwatch.Frequency));
                output.Write(Magic); output.Write(1); output.Write(frame.Rate); output.Write(frame.Count);
                output.Write(writtenNs); output.Write(capturedNs);
                for (int i = 0; i < frame.Count; i++) output.Write(frame.Samples[i]);
                output.Flush();
            }
            finally { Array.Clear(frame.Samples, 0, frame.Samples.Length); lock (Gate) Free.Push(frame); }
        }
    }
    private static void Status(BinaryWriter output, int code) { output.Write(Magic); output.Write(2); output.Write(code); output.Flush(); }
    private static void ClearQueue()
    {
        lock (Gate) while (Ready.Count > 0) { Frame frame = Ready.Dequeue(); Array.Clear(frame.Samples, 0, frame.Samples.Length); Free.Push(frame); }
    }
    private static int SelfTest()
    {
        if (Math.Abs(Decode(new byte[] { 0, 64 }, 0, 16, false) - .5f) > .0001) return 1;
        if (Math.Abs(Decode(new byte[] { 0, 0, 192 }, 0, 24, false) + .5f) > .0001) return 2;
        if (Math.Abs(Decode(BitConverter.GetBytes(.25f), 0, 32, true) - .25f) > .0001) return 3;
        if (Decode(BitConverter.GetBytes(float.NaN), 0, 32, true) != 0) return 4;
        Console.WriteLine("PCM16/24/float32 and non-finite decoding: PASS; no capture performed");
        return 0;
    }
}
