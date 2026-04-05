function execute(url, page) {
  var pageUrl = page ? url + "trang-" + page + "/" : url;
  var response = fetch(pageUrl);
  var doc = response.html();
  var items = doc.select("div.row[itemtype]");
  var results = [];

  for (var i = 0; i < items.length; i++) {
    var item = items[i];
    var anchor = item.selectFirst("h3.truyen-title a");
    if (!anchor) continue;

    var link = anchor.attr("href");
    var name = anchor.text();
    var coverEl = item.selectFirst("div[data-image]");
    var cover = coverEl ? coverEl.attr("data-image") : "";
    var authorEl = item.selectFirst("span.author");
    var author = authorEl ? authorEl.text() : "";

    results.push({ name: name, link: link, cover: cover, description: author });
  }

  var nextEl = doc.selectFirst("li.next a");
  var next = nextEl ? String(page ? parseInt(page) + 1 : 2) : null;

  return Response.success(results, next);
}