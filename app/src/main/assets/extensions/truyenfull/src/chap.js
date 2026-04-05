function execute(url) {
  var response = fetch(url);
  var doc = response.html();

  var contentEl = doc.selectFirst("div#chapter-c");
  if (!contentEl) contentEl = doc.selectFirst("div.chapter-c");

  var content = contentEl ? contentEl.html() : "";
  return Response.success(content);
}