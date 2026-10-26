"""After Gradle Sync/build: python3 tools/live_check.py (requires JDK 17 and Python 3)."""
from pathlib import Path
import os
import sys
import shutil
import subprocess
import tempfile
import urllib.parse

project = Path(__file__).resolve().parents[1]
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'caches/modules-2/files-2.1'
java_root = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else None
suffix = '.exe' if os.name == 'nt' else ''
def executable(name):
    candidate = java_root / (name + suffix) if java_root else None
    result = str(candidate) if candidate and candidate.exists() else shutil.which(name)
    if not result:
        raise SystemExit(f'{name} not found. Install JDK 17 or set JAVA_HOME.')
    return result
try:
    jars = [next((cache / 'org.jsoup/jsoup/1.18.3').rglob('*.jar')), next((cache / 'org.json/json/20240303').rglob('*.jar'))]
except StopIteration:
    raise SystemExit('Run Gradle Sync and testDebugUnitTest first to download dependencies.')
classpath = os.pathsep.join(map(str, jars))
source = project / 'app/src/main/java/dev/enadorry/kaimonoscan'
with tempfile.TemporaryDirectory(prefix='kaimono-live-') as output:
    subprocess.run([executable('javac'), '-cp', classpath, '-d', output, str(source / 'SevenLookup.java'), str(source / 'Pricing.java'), str(source / 'SearchTerms.java'), str(project / 'tools/SevenLive.java')], check=True)
    options = []
    proxy = os.environ.get('HTTPS_PROXY', '')
    if proxy:
        parsed = urllib.parse.urlparse(proxy)
        if parsed.hostname and parsed.port:
            options += [f'-Dhttps.proxyHost={parsed.hostname}', f'-Dhttps.proxyPort={parsed.port}']
    trust_store = os.environ.get('KAIMONO_TRUST_STORE')
    if trust_store:
        options += [f'-Djavax.net.ssl.trustStore={trust_store}']
    subprocess.run([executable('java'), *options, '-cp', classpath + os.pathsep + output, 'dev.enadorry.kaimonoscan.SevenLive', *sys.argv[1:]], check=True)
