"""Offline unit tests; never starts Minecraft. Supply a JUnit console standalone JAR."""
from pathlib import Path
import sys,subprocess,os
r=Path(__file__).resolve().parents[1];junit=Path(sys.argv[1]).resolve()
cp=(r/'build/local-classpath.txt').read_text(); main=r/'build/classes/java/main';out=r/'build/classes/java/test';out.mkdir(parents=True,exist_ok=True)
c=subprocess.run(['java','-Xmx512M','-m','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-encoding','UTF-8','-proc:none','-cp',os.pathsep.join(map(str,[main,cp,junit])),'-d',str(out),*map(str,(r/'src/test/java').rglob('*.java'))])
if c.returncode:sys.exit(c.returncode)
result=subprocess.run(['java','-Xmx512M','-jar',str(junit),'execute','--class-path',os.pathsep.join(map(str,[main,out,cp])),'--scan-class-path='+str(out),'--exclude-classname','.*LrclibLiveTest','--reports-dir',str(r/'build/test-results'),'--details','summary'],capture_output=True,text=True)
(r/'build/test-results.txt').write_text(result.stdout+result.stderr);print(result.stdout,result.stderr);sys.exit(result.returncode)
