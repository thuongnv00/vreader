function execute(key, page) {
  var pageNum = page ? page : 1;
  var url = "https://truyenfull.vision/tim-kiem/?tukhoa=" + encodeURIComponent(key) + "&page=" + pageNum;
  var response = fetch(url);
  var doc = response.html();
  var items = doc.select("div.row[itemtype]");
  var results = [];

  for (var i = 0; i < items.length; i++) {
    var item = items[i];
    var anchor = item.selectFirst("h3.truyen-title a");
    if (!anchor) continue;
    var coverEl = item.selectFirst("div[data-image]");
    results.push({
      name: anchor.text(),
      link: anchor.attr("href"),
      cover: coverEl ? coverEl.attr("data-image") : ""
    });
  }

  var nextEl = doc.selectFirst("li.next a");
  var next = nextEl ? String(parseInt(pageNum) + 1) : null;

  return Response.success(results, next);
}