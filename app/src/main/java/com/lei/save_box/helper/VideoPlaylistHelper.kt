package com.lei.save_box.helper

import com.lei.save_box.model.FileItem

object VideoPlaylistHelper {

    /** 匹配末尾序号标记的正则：数字、EP01、第5集、S01E02 等 */
    private val EPISODE_PATTERN = Regex("[-_\\s.]*(\\d+|[Ee][Pp]?\\d+|[第]\\d+[集话期]|[Ss]\\d+[Ee]\\d+)$")

    data class PlaylistResult(
        val paths: List<String>,
        val startIndex: Int
    )

    /**
     * 从视频列表中找出与目标视频相似名称的视频组
     * @return 播放列表结果，如果同组不足 2 个视频则返回 null
     */
    fun findSimilarPlaylist(
        targetVideo: FileItem,
        allVideos: List<FileItem>
    ): PlaylistResult? {
        if (allVideos.size < 2) return null

        // 按基础名称分组
        val groups = allVideos.groupBy { extractBaseName(it.name) }
            .filter { it.key.isNotEmpty() && it.value.size >= 2 }

        // 找到目标视频所在的分组
        val targetBase = extractBaseName(targetVideo.name)
        val group = groups[targetBase] ?: return null

        // 按文件名自然排序
        val sorted = group.sortedBy { it.name }
        val index = sorted.indexOfFirst { it.path == targetVideo.path }
        val paths = sorted.map { it.path }

        return PlaylistResult(paths = paths, startIndex = index)
    }

    /**
     * 提取文件的基础名称（去掉扩展名 → 去掉末尾序号）
     */
    private fun extractBaseName(fileName: String): String {
        val nameWithoutExt = fileName.substringBeforeLast('.')
        return EPISODE_PATTERN.replace(nameWithoutExt, "").trim()
    }
}
