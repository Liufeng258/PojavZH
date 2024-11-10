package com.movtery.zalithlauncher.ui.fragment

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.mod.modloader.ModVersionListAdapter
import com.movtery.zalithlauncher.feature.mod.modloader.NeoForgeDownloadTask
import com.movtery.zalithlauncher.feature.mod.modloader.NeoForgeUtils.Companion.addAutoInstallArgs
import com.movtery.zalithlauncher.feature.mod.modloader.NeoForgeUtils.Companion.downloadNeoForgeVersions
import com.movtery.zalithlauncher.feature.mod.modloader.NeoForgeUtils.Companion.downloadNeoForgedForgeVersions
import com.movtery.zalithlauncher.feature.mod.modloader.NeoForgeUtils.Companion.formatGameVersion
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.SelectRuntimeDialog
import com.movtery.zalithlauncher.ui.subassembly.modlist.ModListAdapter
import com.movtery.zalithlauncher.ui.subassembly.modlist.ModListFragment
import com.movtery.zalithlauncher.ui.subassembly.modlist.ModListItemBean
import net.kdt.pojavlaunch.JavaGUILauncherActivity
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy
import org.jackhuang.hmcl.util.versioning.VersionNumber
import java.io.File
import java.util.concurrent.Future
import java.util.function.Consumer

class DownloadNeoForgeFragment : ModListFragment(), ModloaderDownloadListener {
    companion object {
        const val TAG: String = "DownloadNeoForgeFragment"
    }

    private val modloaderListenerProxy = ModloaderListenerProxy()

    override fun init() {
        setIcon(ContextCompat.getDrawable(fragmentActivity!!, R.drawable.ic_neoforge))
        setTitleText("NeoForge")
        setLink("https://neoforged.net/")
        setMCMod("https://www.mcmod.cn/class/11433.html")
        setReleaseCheckBoxGone() //隐藏“仅展示正式版”选择框，在这里没有用处
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
                processModDetails(loadVersionList(force))
            }.getOrElse { e ->
                TaskExecutors.runInUIThread {
                    componentProcessing(false)
                    setFailedToLoad(e.toString())
                }
                Logging.e("DownloadNeoForgeFragment", Tools.printToString(e))
            }
        }
    }

    @Throws(Exception::class)
    fun loadVersionList(force: Boolean): List<String?> {
        val versions: MutableList<String?> = ArrayList()
        versions.addAll(downloadNeoForgedForgeVersions(force))
        versions.addAll(downloadNeoForgeVersions(force))

        versions.reverse()

        return versions
    }

    private fun processModDetails(neoForgeVersions: List<String?>?) {
        neoForgeVersions ?: run {
            TaskExecutors.runInUIThread {
                componentProcessing(false)
                setFailedToLoad("neoForgeVersions is Empty!")
            }
            return
        }

        val mNeoForgeVersions: MutableMap<String, MutableList<String?>> = HashMap()
        neoForgeVersions.forEach(Consumer<String?> { neoForgeVersion: String? ->
            currentTask?.apply { if (isCancelled) return@Consumer }
            //查找并分组Minecraft版本与NeoForge版本
            val gameVersion: String

            val isOldVersion = neoForgeVersion!!.contains("1.20.1")
            gameVersion = if (isOldVersion) {
                "1.20.1"
            } else if (neoForgeVersion == "47.1.82") {
                return@Consumer
            } else { //1.20.2+
                formatGameVersion(neoForgeVersion)
            }
            addIfAbsent(mNeoForgeVersions, gameVersion, neoForgeVersion)
        })

        currentTask?.apply { if (isCancelled) return }

        val mData: MutableList<ModListItemBean> = ArrayList()
        mNeoForgeVersions.entries
            .sortedWith { entry1, entry2 -> -VersionNumber.compare(entry1.key, entry2.key) }
            .forEach { entry: Map.Entry<String, List<String?>> ->
                currentTask?.apply { if (isCancelled) return }
                val adapter = ModVersionListAdapter(modloaderListenerProxy, this, R.drawable.ic_neoforge, entry.value)

                adapter.setOnItemClickListener { version: Any? ->
                    if (isTaskRunning()) return@setOnItemClickListener false
                    Thread(NeoForgeDownloadTask(modloaderListenerProxy, (version as String?)!!)).start()
                    true
                }
                mData.add(ModListItemBean("Minecraft " + entry.key, adapter))
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
        TaskExecutors.runInUIThread {
            val modInstallerStartIntent = Intent(fragmentActivity!!, JavaGUILauncherActivity::class.java)
            addAutoInstallArgs(modInstallerStartIntent, downloadedFile)
            SelectRuntimeDialog(fragmentActivity!!).apply {
                setListener { jreName: String? ->
                    modloaderListenerProxy.detachListener()
                    modInstallerStartIntent.putExtra(JavaGUILauncherActivity.EXTRAS_JRE_NAME, jreName)
                    dismiss()
                    Tools.backToMainMenu(fragmentActivity!!)
                    fragmentActivity?.startActivity(modInstallerStartIntent)
                }
                setTitleText(R.string.create_profile_neoforge)
                show()
            }
        }
    }

    override fun onDataNotAvailable() {
        TaskExecutors.runInUIThread {
            modloaderListenerProxy.detachListener()
            Tools.dialog(fragmentActivity!!, fragmentActivity!!.getString(R.string.generic_error), fragmentActivity!!.getString(R.string.mod_no_installer, "NeoForge"))
        }
    }

    override fun onDownloadError(e: Exception) {
        TaskExecutors.runInUIThread {
            modloaderListenerProxy.detachListener()
            Tools.showError(fragmentActivity!!, e)
        }
    }
}
