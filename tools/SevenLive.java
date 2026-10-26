package dev.enadorry.kaimonoscan;
public class SevenLive {
 public static void main(String[] args) throws Exception {
  var results=SevenLookup.search(args.length==0?"ファイア":args[0]);
  if(results.links.isEmpty())throw new AssertionError("No live results");
  SevenLookup.Link selected=null;
  for(var link:results.links)if(args.length>0||link.name.contains("ブラック")){selected=link;break;}
  if(selected==null)throw new AssertionError("Black coffee not in live results");
  var product=SevenLookup.load(selected.url);
  if(product.cents<=0||(args.length==0&&!product.name.contains("ブラック")))throw new AssertionError("Invalid product");
  var cached=SevenLookup.Product.read(product.json());
  if(!SevenLookup.fresh(cached,product.checkedAt))throw new AssertionError("Cache failed");
  System.out.println("Used query: "+results.query);
  System.out.println("Live search: "+results.links.size()+" candidates");
  System.out.println("Product: "+product.name);
  System.out.println("Displayed price: "+product.priceText);
  System.out.println("Region: "+product.region);
  System.out.println("URL: "+product.url);
  System.out.println("Fractional subtotal, 2 units: "+Pricing.fractionalSubtotal(product.cents,2));
  System.out.println("LIVE SEARCH + DETAIL + CACHE: PASS");
 }
}
