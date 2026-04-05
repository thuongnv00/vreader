function execute(url) {
  var response = fetch(url);
  var doc = response.html();
  var items = doc.select("ul.list-chapter li a");
  var results = [];

  for (var i = 0; i < items.length; i++) {
    var a = items[i];
    results.push({
      name: a.text(),
      url: a.attr("href")
    });
  }

  return Response.success(results);
}