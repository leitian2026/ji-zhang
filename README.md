# 超市记账 (ShopLedger)

输入框里记账，上面实时出账；一趟逛完点右上角"结束"结算，下一趟从 0 开始。

- 输入：`5` / `苹果5` / `面条10，2`（单价，数量）；多笔用换行或分号隔开
- 构建：GitHub → Actions → Build Release APK → Run workflow（仅手动触发）
- 产物：运行完成后在该次运行页面底部 Artifacts 下载 ShopLedger-release

覆盖安装：签名固定（release.jks），applicationId 不变，versionCode 每次构建自动递增，所以新版可以直接覆盖旧版，数据保留。
