function execute(url) {
  var response = fetch(url);
  var doc = response.html();

  var name = doc.selectFirst("h3.title") ? doc.selectFirst("h3.title").text() : "";
  var cover = doc.selectFirst("div.book img") ? doc.selectFirst("div.book img").attr("src") : "";
  var authorEl = doc.selectFirst("a[itemprop=author]");
  var author = authorEl ? authorEl.text() : "";
  var descEl = doc.selectFirst("div.desc-text");
  var description = descEl ? descEl.text() : "";
  var statusEl = doc.selectFirst("span.text-success, span.text-primary");
  var ongoing = statusEl ? statusEl.text().indexOf("Hoàn") === -1 : true;

  return Response.success({
    name: name,
    cover: cover,
    author: author,
    description: description,
    ongoing: ongoing
  });
}