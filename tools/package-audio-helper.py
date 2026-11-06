"""Package the explicitly built Windows helper. Never downloads anything at game runtime."""
from pathlib import Path
import hashlib,json,shutil
root=Path(__file__).resolve().parent.parent
built=root/'native/SkyLyrics.AudioBridge/bin/Release/net472'
target=root/'src/main/resources/skylyrics/audio/windows'
target.mkdir(parents=True,exist_ok=True)
files=[]
for name in ['SkyLyrics.AudioBridge.exe','SkyLyrics.AudioBridge.exe.config','NAudio.Core.dll','NAudio.Wasapi.dll']:
    source=built/name
    shutil.copy2(source,target/name)
    files.append({'name':name,'sha256':hashlib.sha256(source.read_bytes()).hexdigest()})
(target/'manifest.json').write_text(json.dumps({'protocol':2,'target':'.NET Framework 4.7.2','source':'native/SkyLyrics.AudioBridge','naudio':'2.2.1','files':files},indent=2)+'\n')
print('Packaged Windows SYSTEM loopback helper and pinned SHA-256 manifest')
