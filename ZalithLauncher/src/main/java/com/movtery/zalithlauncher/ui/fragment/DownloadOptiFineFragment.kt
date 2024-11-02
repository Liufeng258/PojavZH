package com.movtery.zalithlauncher.ui.fragment

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.mod.modloader.ModVersionListAdapter
import com.movtery.zalithlauncher.feature.mod.modloader.OptiFineDownloadType
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.SelectRuntimeDialog
import com.movtery.zalithlauncher.ui.subassembly.modlist.ModListAdapter
import com.movtery.zalithlauncher.ui.subassembly.modlist.ModListFragment
import com.movtery.zalithlauncher.ui.subassembly.modlist.ModListItemBean
import net.kdt.pojavlaunch.JavaGUILauncherActivity
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy
import net.kdt.pojavlaunch.modloaders.OptiFineDownloadTask
import net.kdt.pojavlaunch.modloaders.OptiFineUtils
import net.kdt.pojavlaunch.modloaders.OptiFineUtils.OptiFineVersion
import net.kdt.pojavlaunch.modloaders.OptiFineUtils.OptiFineVersions
import org.jackhuang.hmcl.util.versioning.VersionNumber
import java.io.File
import java.util.concurrent.Future
import java.util.function.Consumer

class DownloadOptiFineFragment : ModListFragment(), ModloaderDownloadListener {
    companion object {
        const val TAG: String = "DownloadOptiFineFragment"
        const val BUNDLE_DOWNLOAD_MOD: String = "bundle_download_mod"
    }

    private val modloaderListenerProxy = ModloaderListenerProxy()
    private var mIsDownloadMod = false

    override fun init() {
        setIcon(ContextCompat.getDrawable(fragmentActivity!!, R.drawable.ic_optifine))
        setTitleText("OptiFine")
        setLink("https://www.optifine.net/home")
        setMCMod("https://www.mcmod.cn/class/36.html")
        setReleaseCheckBoxGone()
        parseBundle()
        super.init()
    }

    override fun initRefresh(): Future<*> {
        return refresh(false)
    }

    override fun refresh(): Future<*> {
        return refresh(true)
    }

    private fun refresh(force: Boolean): Future<*> {
        return TaskExecutors.getDefault().submit {
            runCatching {
                TaskExecutors.runInUIThread {
                    cancelFailedToLoad()
                    componentProcessing(true)
                }
                val optiFineVersions = OptiFineUtils.downloadOptiFineVersions(force)
                processModDetails(optiFineVersions)
            }.getOrElse { e ->
                TaskExecutors.runInUIThread {
                    componentProcessing(false)
                    setFailedToLoad(e.toString())
                }
                Logging.e("DownloadOptiFineFragment", Tools.printToString(e))
            }
        }
    }

    private fun parseBundle() {
        val bundle = arguments ?: return
        mIsDownloadMod = bundle.getBoolean(BUNDLE_DOWNLOAD_MOD, false)
    }

    private fun processModDetails(optiFineVersions: OptiFineVersions?) {
        optiFineVersions ?: run {
            TaskExecutors.runInUIThread {
                componentProcessing(false)
                setFailedToLoad("optiFineVersions is Empty!")
            }
            return
        }

        val mOptiFineVersions: MutableMap<String, MutableList<OptiFineVersion?>> = HashMap()
        optiFineVersions.optifineVersions.forEach(Consumer<List<OptiFineVersion>> { optiFineVersionList: List<OptiFineVersion> ->  //通过版本列表一层层遍历并合成为 Minecraft版本 + Optifine版本的Map集合
            currentTask?.apply { if (isCancelled) return@Consumer }

            optiFineVersionList.forEach(Consumer Consumer2@{ optiFineVersion: OptiFineVersion ->
                currentTask?.apply { if (isCancelled) return@Consumer2 }
                mOptiFineVersions.computeIfAbsent(optiFineVersion.minecraftVersion) { ArrayList() }
                    .add(optiFineVersion)
            })
        })

        if (currentTask!!.isCancelled) return

        val mData: MutableList<ModListItemBean> = ArrayList()
        mOptiFineVersions.entries
            .sortedWith { entry1, entry2 -> -VersionNumber.compare(entry1.key, entry2.key) }
            .forEach { entry: Map.Entry<String, List<OptiFineVersion?>> ->
                currentTask?.apply { if (isCancelled) return@forEach }

                val adapter = ModVersionListAdapter(modloaderListenerProxy, this, R.drawable.ic_optifine, entry.value)

                adapter.setOnItemClickListener { version: Any? ->
                    if (isTaskRunning()) return@setOnItemClickListener false
                    Thread(OptiFineDownloadTask(version as OptiFineVersion?, modloaderListenerProxy,
                            if (mIsDownloadMod) OptiFineDownloadType.DOWNLOAD_MOD else OptiFineDownloadType.DOWNLOAD_GAME)
                    ).start()
                    true
                }
                mData.add(ModListItemBean(entry.key, adapter))
            }

        currentTask?.apply { if (isCancelled) return }

        TaskExecutors.runInUIThread {
            val recyclerView = recyclerView
            runCatching {
                var mModAdapter = recyclerView.adapter as ModListAdapter?
                mModAdapter ?: run {
                    mModAdapter = ModListAdapter(this, mData)
                    recyclerView.layoutManager = LinearLayoutManager(fragmentActivity!!)
                    recyclerView.adapter = mModAdapter
                    return@runCatching
                }
                mModAdapter?.updateData(mData)
            }.getOrElse { e ->
                Logging.e("Set Adapter", Tools.printToString(e))
            }

            componentProcessing(false)
            recyclerView.scheduleLayoutAnimation()
        }
    }

    override fun onDownloadFinished(downloadedFile: File) {
        if (!mIsDownloadMod) {
            TaskExecutors.runInUIThread {
                val modInstallerStartIntent = Intent(fragmentActivity!!, JavaGUILauncherActivity::class.java)
                OptiFineUtils.addAutoInstallArgs(modInstallerStartIntent, downloadedFile)
                SelectRuntimeDialog(fragmentActivity!!).apply {
                    setListener { jreName: String? ->
                        modloaderListenerProxy.detachListener()
                        modInstallerStartIntent.putExtra(JavaGUILauncherActivity.EXTRAS_JRE_NAME, jreName)
                        dismiss()
                        Tools.backToMainMenu(fragmentActivity!!)
                        fragmentActivity?.startActivity(modInstallerStartIntent)
                    }
                    setTitleText(R.string.create_profile_optifine)
                    show()
                }
            }
        }
    }

    override fun onDataNotAvailable() {
        TaskExecutors.runInUIThread {
            modloaderListenerProxy.detachListener()
            Tools.dialog(fragmentActivity!!, fragmentActivity!!.getString(R.string.generic_error), fragmentActivity!!.getString(R.string.mod_optifine_failed_to_scrape))
        }
    }

    override fun onDownloadError(e: Exception) {
        TaskExecutors.runInUIThread {
            modloaderListenerProxy.detachListener()
            Tools.showError(fragmentActivity!!, e)
        }
    }
}
