"""Bundle the official Korean Vosk model; no user's audio or API key is used."""
from pathlib import Path
from urllib.request import urlopen
import shutil
import zipfile
import hashlib

base = Path(__file__).resolve().parents[1]
archive = base / 'model-download.zip'
url = 'https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip'
with urlopen(url, timeout=90) as response, archive.open('wb') as output:
    shutil.copyfileobj(response, output)
print('Korean model archive SHA256:', hashlib.sha256(archive.read_bytes()).hexdigest())
assets = base / 'app/src/main/assets'
assets.mkdir(parents=True, exist_ok=True)
model = assets / 'wake-model'
if model.exists():
    shutil.rmtree(model)
with zipfile.ZipFile(archive) as zipped:
    prefix = 'vosk-model-small-ko-0.22/'
    for item in zipped.infolist():
        if not item.filename.startswith(prefix):
            raise ValueError('Unexpected model archive entry')
        relative = Path(item.filename[len(prefix):])
        if '..' in relative.parts or relative.is_absolute():
            raise ValueError('Unsafe model archive entry')
        destination = model / relative
        if item.is_dir():
            destination.mkdir(parents=True, exist_ok=True)
        else:
            destination.parent.mkdir(parents=True, exist_ok=True)
            with zipped.open(item) as source, destination.open('wb') as output:
                shutil.copyfileobj(source, output)
assert (model / 'am/final.mdl').is_file()
assert (model / 'conf/model.conf').is_file()
archive.unlink()
print('Bundled Korean model:', sum(p.stat().st_size for p in model.rglob('*') if p.is_file()), 'bytes')

words = model / 'graph/words.txt'
if words.exists():
    vocabulary = words.read_text(errors='replace')
    print('Wake vocabulary hints:', [line for line in vocabulary.splitlines() if any(w in line for w in ['자비스', '자비', '비스'])][:30])
