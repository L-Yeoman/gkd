package li.songe.gkd.ui

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.AdModel
import li.songe.gkd.util.allRulesFlow
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.appRuleFlow
import li.songe.gkd.util.checkShowAd
import li.songe.gkd.util.clickCountFlow
import li.songe.gkd.util.getAdByte
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.shareDir
import li.songe.gkd.util.subsIdToRawFlow
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ControlVm @Inject constructor() : ViewModel() {
    val adModel = MutableStateFlow<AdModel?>(null)
    val adPic = MutableStateFlow<ByteArray?>(null)

    init {
        appScope.launchTry(Dispatchers.IO){
            val _AdInfo: SharedPreferences = app.getSharedPreferences("Ad", MODE_PRIVATE)
            val adByte = _AdInfo?.getString("adbyte", "")
            val jumpUrl = _AdInfo?.getString("jumpUrl", "")
            val timeStamp = _AdInfo?.getLong("timestamp", 0)?:0
            val isShow = _AdInfo?.getInt("isShow", 1)?:1
            val curTime = System.currentTimeMillis()/1000L
            LogUtils.i("AdTest","本地获取ad数据,isShow:$isShow,jumpUrl:$jumpUrl,timeStamp:$timeStamp")
            // if(adByte?.isNotEmpty() == true&& jumpUrl?.isNotEmpty() == true&&curTime<timeStamp){
            if(curTime<timeStamp){
                adPic.value = adByte?.decodeBase64Bytes()
                adModel.value = AdModel(jumpUrl?:"","",0L,isShow,"")
                // LogUtils.i("AdTest","拿到本地ad数据,adPic.value:${adPic.value}")
            }else{
                getAdInfo()
            }
        }
    }
    //0显示  1不显示
    public fun adShow(value:Int?)= value==0


    private suspend fun getAdInfo(){
        try {
            val _AdModel = checkShowAd()
            adPic.value = getAdByte(_AdModel!!.url)
            adModel.value = _AdModel
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val adInfo: SharedPreferences = app.getSharedPreferences("Ad", MODE_PRIVATE)
        val editor = adInfo.edit()
        val delayTime = adModel.value?.delayTime?:86400L//默认5天
        // val delayTime = 259200L //默认5天

        editor.putString("adbyte",adPic.value?.encodeBase64())
        editor.putString("jumpUrl",adModel.value?.jumpUrl)
        editor.putInt("isShow",adModel.value?.isShow?:1)
        editor.putLong("timestamp", System.currentTimeMillis()/1000L+delayTime)
        editor.commit() //提交修改
        saveShareImg(adModel.value!!.shareApp)
        // LogUtils.i("AdTest","请求网络数据并保存,adbyte:${adPic.value?.encodeBase64()},isShow:${adModel.value?.isShow},jumpUrl:${adModel.value?.jumpUrl},timestamp:${System.currentTimeMillis()/1000L+300L}")
    }

    private suspend fun saveShareImg(url:String){
        if(url?.isEmpty() == true){
            return
        }
        val file = File(shareDir.getAbsolutePath(), "shareImg.jpg")
        val imgByte= getAdByte(url)
        val bmp: Bitmap =
            BitmapFactory.decodeByteArray(imgByte, 0, imgByte.size)
        try {
            val outStream = FileOutputStream(file)
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
            outStream.write(imgByte)
            outStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    private val latestRecordFlow =
        DbSet.clickLogDao.queryLatest().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val latestRecordDescFlow = combine(
        latestRecordFlow, subsIdToRawFlow, appInfoCacheFlow
    ) { latestRecord, subsIdToRaw, appInfoCache ->
        if (latestRecord == null) return@combine null
        val groupName =
            subsIdToRaw[latestRecord.subsId]?.apps?.find { a -> a.id == latestRecord.appId }?.groups?.find { g -> g.key == latestRecord.groupKey }?.name
        val appName = appInfoCache[latestRecord.appId]?.name
        val appShowName = appName ?: latestRecord.appId ?: ""
        if (groupName != null) {
            if (groupName.contains(appShowName)) {
                groupName
            } else {
                "$appShowName-$groupName"
            }
        } else {
            appShowName
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val subsStatusFlow = combine(allRulesFlow, clickCountFlow) { allRules, clickCount ->
        allRules.numText + if (clickCount > 0) {
            "/${clickCount}点击"
        } else {
            ""
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

}