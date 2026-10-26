import urllib.request,time,re,json,hashlib,argparse,tempfile
from datetime import datetime,timezone,timedelta
from pathlib import Path
from urllib.parse import urljoin,urlsplit
from bs4 import BeautifulSoup
parser=argparse.ArgumentParser(description='Refresh public product names and official URLs (no JAN/price inference). Requires beautifulsoup4.')
parser.add_argument('--cache',type=Path,default=Path(tempfile.mkdtemp(prefix='kaimono-store-catalog-')))
parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'app/src/main/assets/store-products.json')
args=parser.parse_args()
base=args.cache;base.mkdir(parents=True,exist_ok=True)
def get(u):
 p=base/(hashlib.sha256(u.encode()).hexdigest()[:16]+'.html')
 if p.exists():return BeautifulSoup(p.read_text(encoding="utf-8"),'html.parser')
 time.sleep(1.2)
 data=urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'KaimonoScan catalog updater/0.10'}),timeout=25).read().decode('utf-8')
 p.write_text(data,encoding="utf-8");return BeautifulSoup(data,'html.parser')
indexes={'lawson':'https://www.lawson.co.jp/recommend/original/','family':'https://www.family.co.jp/goods.html'}
cats={};products={};failures=[]
for store,index in indexes.items():
 soup=get(index)
 if store=='lawson':
  extra=get(urljoin(index,'select/'))
  anchors=list(soup.select('a[href]'))+list(extra.select('a[href]'))
 else:anchors=soup.select('a[href]')
 for a in anchors:
  url=urljoin(index,a['href']).split('#')[0].split('?')[0];path=urlsplit(url).path
  valid=(store=='lawson' and urlsplit(url).netloc=='www.lawson.co.jp' and re.fullmatch(r'/recommend/original/(?:[a-z_]+/|select/[a-z_]+/(?:index.html)?)',path)) or (store=='family' and urlsplit(url).netloc=='www.family.co.jp' and re.fullmatch(r'/goods/[a-z0-9_]+.html',path) and not any(s in path for s in ['newgoods','safety','allergen','goyoyaku','charakuji','disc']))
  if valid and a.get_text(strip=True) and a.get_text(strip=True)!='もっとみる':cats[url]=(store,a.get_text(' ',strip=True))
for url,(store,category) in cats.items():
 try:
  soup=get(url);count=0
  cards=soup.select('li') if store=='lawson' else soup.select('.ly-mod-infoset3')
  for card in cards:
   if store=='lawson':a=card.select_one('a[href*="/recommend/original/detail/"]');name=card.select_one('p.ttl')
   else:a=card.select_one('a.ly-mod-infoset3-link[href]');name=card.select_one('.ly-mod-infoset3-name')
   if not a or not name:continue
   detail=urljoin(url,a['href']).split('#')[0];path=urlsplit(detail).path
   if not (re.fullmatch(r'/recommend/original/detail/[0-9]+_[0-9]+.html',path) if store=='lawson' else re.fullmatch(r'/goods/[a-z0-9_-]+/[0-9]+.html',path)):continue
   if 'nextWeek' in str(card):continue
   n=name.get_text(' ',strip=True)
   if not n:continue
   products[detail]={'store':store,'name':n,'url':detail,'region':'販売地域は詳細で確認','category':category};count+=1
  print(store,category,count,flush=True)
 except Exception as e:failures.append({'url':url,'error':str(e)});print('ERROR',url,str(e),flush=True)
data={'updatedAt':datetime.now(timezone(timedelta(hours=9))).date().isoformat(),'sources':list(indexes.values()),'products':list(products.values())}
args.output.parent.mkdir(parents=True,exist_ok=True)
destination=args.output.with_suffix('.partial.json') if failures else args.output
destination.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
args.output.with_suffix('.failures.json').write_text(json.dumps(failures,ensure_ascii=False));print('TOTAL',len(products),flush=True)

raise SystemExit(1 if failures else 0)
