package li.songe.gkd.util

const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
// const val UPDATE_URL = "https://registry.npmmirror.com/@gkd-kit/app/latest/files/index.json"
const val UPDATE_URL = "https://registry.npmmirror.com/kyd/latest/files/index.json"
const val AD_URL = "https://registry.npmmirror.com/kyd/latest/files/ad.json"



const val FILE_UPLOAD_URL = "https://u.gkd.li/"//日志分享
const val IMPORT_BASE_URL = "https://i.gkd.li/import/"//上传快照
const val DEFAULT_SUBS_UPDATE_URL = "https://registry.npmmirror.com/@gkd-kit/subscription/latest/files" //规则
const val SERVER_SCRIPT_URL = "https://registry-direct.npmmirror.com/@gkd-kit/config/latest/files/dist/server.js"//快照Http服务
// const val REPOSITORY_URL = "https://github.com/gkd-kit/gkd"
//可信来源
val safeRemoteBaseUrls = arrayOf(
    "https://registry.npmmirror.com/@gkd-kit/",
    "https://cdn.jsdelivr.net/npm/@gkd-kit/",
    "https://fastly.jsdelivr.net/npm/@gkd-kit/",
    "https://unpkg.com/@gkd-kit/",
    "https://github.com/gkd-kit/",
    "https://raw.githubusercontent.com/gkd-kit/"
)
