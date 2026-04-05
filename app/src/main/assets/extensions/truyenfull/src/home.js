function execute() {
  return Response.success([
    {
      title: "Truyện Hot",
      input: "https://truyenfull.vision/danh-sach/truyen-hot/",
      script: "homecontent"
    },
    {
      title: "Mới cập nhật",
      input: "https://truyenfull.vision/danh-sach/truyen-moi/",
      script: "homecontent"
    }
  ]);
}