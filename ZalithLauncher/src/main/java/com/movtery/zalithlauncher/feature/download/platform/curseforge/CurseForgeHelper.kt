package com.movtery.zalithlauncher.feature.download.platform.curseforge

import com.movtery.zalithlauncher.feature.download.enums.Classify
import com.movtery.zalithlauncher.feature.download.install.InstallHelper
import com.movtery.zalithlauncher.feature.download.install.UnpackWorldZipHelper
import com.movtery.zalithlauncher.feature.download.item.InfoItem
import com.movtery.zalithlauncher.feature.download.item.ModLoaderWrapper
import com.movtery.zalithlauncher.feature.download.item.ScreenshotItem
import com.movtery.zalithlauncher.feature.download.item.SearchResult
import com.movtery.zalithlauncher.feature.download.item.VersionItem
import com.movtery.zalithlauncher.feature.download.platform.AbstractPlatformHelper
import com.movtery.zalithlauncher.feature.download.platform.curseforge.CurseForgeCommonUtils.Companion.CURSEFORGE_MODPACK_CLASS_ID
import com.movtery.zalithlauncher.feature.download.platform.curseforge.CurseForgeCommonUtils.Companion.CURSEFORGE_MOD_CLASS_ID
import com.movtery.zalithlauncher.feature.download.utils.PlatformUtils
import java.io.File

class CurseForgeHelper : AbstractPlatformHelper(PlatformUtils.createCurseForgeApi()) {
    override fun copy(): AbstractPlatformHelper {
        val new = CurseForgeHelper()
        new.filters = this.filters
        new.currentClassify = this.currentClassify
        return new
    }

    //更换为使用 slug 拼接链接
    override fun getWebUrl(infoItem: InfoItem): String? {
        return "https://www.curseforge.com/minecraft/${
            when (currentClassify) {
                Classify.ALL -> return null
                Classify.MOD -> "mc-mods"
                Classify.MODPACK -> "modpacks"
                Classify.RESOURCE_PACK -> "texture-packs"
                Classify.WORLD -> "worlds"
            }
        }/${infoItem.slug}"
    }

    override fun getScreenshots(projectId: String): List<ScreenshotItem> {
        return CurseForgeCommonUtils.getScreenshots(api, projectId)
    }

    @Throws(Throwable::class)
    override fun searchMod(lastResult: SearchResult): SearchResult? {
        return CurseForgeModHelper.modLikeSearch(api, lastResult, filters, CURSEFORGE_MOD_CLASS_ID)
    }

    @Throws(Throwable::class)
    override fun searchModPack(lastResult: SearchResult): SearchResult? {
        return CurseForgeModHelper.modLikeSearch(api, lastResult, filters, CURSEFORGE_MODPACK_CLASS_ID)
    }

    @Throws(Throwable::class)
    override fun searchResourcePack(lastResult: SearchResult): SearchResult? {
        return CurseForgeCommonUtils.getResults(api, lastResult, filters, 12)
    }

    @Throws(Throwable::class)
    override fun searchWorld(lastResult: SearchResult): SearchResult? {
        return CurseForgeCommonUtils.getResults(api, lastResult, filters, 17)
    }

    @Throws(Throwable::class)
    override fun getModVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? {
        return CurseForgeModHelper.getModVersions(api, infoItem, force)
    }

    @Throws(Throwable::class)
    override fun getModPackVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? {
        return CurseForgeModHelper.getModPackVersions(api, infoItem, force)
    }

    @Throws(Throwable::class)
    override fun getResourcePackVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? {
        return CurseForgeCommonUtils.getVersions(api, infoItem, force)
    }

    @Throws(Throwable::class)
    override fun getWorldVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? {
        return CurseForgeCommonUtils.getVersions(api, infoItem, force)
    }

    @Throws(Throwable::class)
    override fun installMod(infoItem: InfoItem, version: VersionItem, targetPath: File?) {
        InstallHelper.downloadFile(version, targetPath)
    }

    @Throws(Throwable::class)
    override fun installModPack(infoItem: InfoItem, version: VersionItem): ModLoaderWrapper? {
        return CurseForgeModPackInstallHelper.startInstall(api, infoItem.copy(), version)
    }

    @Throws(Throwable::class)
    override fun installResourcePack(infoItem: InfoItem, version: VersionItem, targetPath: File?) {
        InstallHelper.downloadFile(version, targetPath)
    }

    @Throws(Throwable::class)
    override fun installWorld(infoItem: InfoItem, version: VersionItem, targetPath: File?) {
        InstallHelper.downloadFile(version, targetPath) { file ->
            targetPath!!.parentFile?.let { UnpackWorldZipHelper.unpackFile(file, it) }
        }
    }
}