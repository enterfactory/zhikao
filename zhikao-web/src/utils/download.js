/**
 * 浏览器本地保存文件流（用于导出、模板下载等场景）。
 * @param {Blob} blob 后端返回的文件流
 * @param {string} filename 保存的文件名
 */
export function saveBlob(blob, filename) {
  const url = window.URL.createObjectURL(new Blob([blob]))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}
