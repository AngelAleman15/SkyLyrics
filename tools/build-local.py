"""Compile against the existing patched NeoForge installation, without launching Minecraft.
Usage: python tools/build-local.py /path/to/client/libraries
This avoids downloading or regenerating the complete Minecraft development bundle.
Gradle/ModDev is also provided for standalone development.
"""
from pathlib import Path
import sys, subprocess, shutil, zipfile, os
root=Path(__file__).resolve().parents[1]
libs=Path(sys.argv[1]); jars=sorted(libs.rglob('*.jar'))
patched=list(libs.rglob('neoforge-21.1.266-client.jar'))
assert len(patched)==1, 'Need the patched NeoForge 21.1.266 client library'
cp=os.pathsep.join(map(str,patched+[p for p in jars if p not in patched]))
out=root/'build/classes/java/main'
if out.exists(): shutil.rmtree(out)
out.mkdir(parents=True)
result = subprocess.run(['java','-Xmx512M','-m','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-encoding','UTF-8','-proc:none','-classpath',cp,'-d',str(out),*map(str,(root/'src/main/java').rglob('*.java'))],check=False)
if result.returncode: sys.exit(result.returncode)
jar=root/'build/libs/skylyrics-1.21.1-0.4.0-neoforge.jar';jar.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nImplementation-Version: 0.4.0-neoforge\n\n')
 for base in [out,root/'src/main/resources']:
  for f in sorted(base.rglob('*')):
   if f.is_file(): z.write(f,f.relative_to(base).as_posix())
print(jar)
(root/'build/local-classpath.txt').write_text(cp)
