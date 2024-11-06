package com.movtery.zalithlauncher.feature.download.utils

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.download.enums.DependencyType

class DependencyUtils {
    companion object {
        fun getDependencyType(type: String?): DependencyType {
            return DependencyType.entries.find { it.curseforge == type || it.modrinth == type }
                ?: DependencyType.REQUIRED
        }

        fun getTextFromType(context: Context, type: DependencyType?): String {
            return when (type) {
                DependencyType.OPTIONAL -> context.getString(R.string.download_install_dependencies_optional)
                DependencyType.INCOMPATIBLE -> context.getString(R.string.download_install_dependencies_incompatible)
                DependencyType.EMBEDDED -> context.getString(R.string.download_install_dependencies_embedded)
                DependencyType.TOOL -> context.getString(R.string.download_install_dependencies_tool)
                DependencyType.INCLUDE -> context.getString(R.string.download_install_dependencies_include)
                DependencyType.REQUIRED -> context.getString(R.string.download_install_dependencies_required)
                else -> context.getString(R.string.download_install_dependencies_required)
            }
        }
    }
}