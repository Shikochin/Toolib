"""Toolib 的媒体引擎桥接层：在进程内用 yt-dlp 解析 / 下载。

Chaquopy 把 CPython 当原生库加载（System.loadLibrary），yt-dlp 直接跑在当前进程里，
所以不需要把任何可执行文件解压到 nativeLibraryDir —— 也就不再需要 legacyPackaging。

约定：所有函数返回 JSON 字符串，异常直接抛给 Kotlin（Chaquopy 会转成 PythonException）。
"""

import json
import os


def _yt_dlp():
    import yt_dlp

    return yt_dlp


def _base_opts():
    return {
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "consoletitle": False,
        "cachedir": False,
        "socket_timeout": 20,
        "retries": 3,
        "noplaylist": True,
        "nocheckcertificate": False,
        # 不做任何后处理：合并 / 转码 / 内嵌全部交给 Kotlin 侧的 ffmpeg-kit
        "postprocessors": [],
        "prefer_ffmpeg": False,
    }


def version():
    """yt-dlp 版本，用于自检。"""
    return _yt_dlp().version.__version__


def _format_info(fmt):
    return {
        "id": fmt.get("format_id") or "",
        "ext": fmt.get("ext") or "",
        "width": int(fmt.get("width") or 0),
        "height": int(fmt.get("height") or 0),
        "fps": int(fmt.get("fps") or 0),
        "vcodec": fmt.get("vcodec") or "",
        "acodec": fmt.get("acodec") or "",
        "tbr": float(fmt.get("tbr") or 0),
        "filesize": int(fmt.get("filesize") or fmt.get("filesize_approx") or 0),
        "note": fmt.get("format_note") or "",
    }


def _entry_info(entry, index):
    return {
        "index": int(entry.get("playlist_index") or index + 1),
        "title": entry.get("title") or "",
        "url": entry.get("webpage_url") or entry.get("url") or "",
        "duration": int(entry.get("duration") or 0),
        "thumbnail": entry.get("thumbnail") or "",
    }


def inspect(url):
    """解析链接，返回标题、封面、时长、可选格式和播放列表条目。"""
    yt_dlp = _yt_dlp()
    opts = _base_opts()
    opts.update({"skip_download": True, "extract_flat": "in_playlist", "noplaylist": False})
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
    if not info:
        raise ValueError("yt-dlp returned no media info")
    entries = info.get("entries") or []
    payload = {
        "title": info.get("title") or url,
        "uploader": info.get("uploader") or info.get("channel") or "",
        "thumbnail": info.get("thumbnail") or "",
        "duration": int(info.get("duration") or 0),
        "webpageUrl": info.get("webpage_url") or url,
        "formats": [_format_info(f) for f in (info.get("formats") or [])],
        "entries": [_entry_info(e, i) for i, e in enumerate(entries) if e],
    }
    return json.dumps(payload, ensure_ascii=False)


def _progress_hook(callback):
    def hook(data):
        if callback is None:
            return
        try:
            status = data.get("status")
            if status == "downloading":
                total = data.get("total_bytes") or data.get("total_bytes_estimate") or 0
                done = data.get("downloaded_bytes") or 0
                percent = (float(done) * 100.0 / float(total)) if total else 0.0
                line = (data.get("_speed_str") or "").strip()
                speed = int(data.get("speed") or 0)
                callback.onProgress(percent, int(data.get("eta") or 0), speed, line)
            elif status == "finished":
                callback.onProgress(100.0, 0, 0, "")
        except Exception:
            # 进度回调失败不能影响下载
            pass

    return hook


def download(url, selectors_json, out_dir, playlist_items_json, callback, subtitle_langs=""):
    """按给定的格式选择器逐个下载到 out_dir/<序号>/。

    selectors_json: 形如 ["bv*[ext=mp4]", "ba[ext=m4a]"] 的 JSON 数组字符串；
    每个选择器的产物放在 out_dir/<序号>/ 里，Kotlin 侧据此区分视频轨和音频轨。
    """
    yt_dlp = _yt_dlp()
    selectors = [str(s) for s in json.loads(selectors_json or "[]")]
    playlist_items = [int(i) for i in json.loads(playlist_items_json or "[]")]
    os.makedirs(out_dir, exist_ok=True)

    groups = []
    title = ""
    duration = 0
    for index, selector in enumerate(selectors):
        target = os.path.join(out_dir, str(index))
        os.makedirs(target, exist_ok=True)
        opts = _base_opts()
        opts.update(
            {
                "format": selector or "best",
                "outtmpl": {"default": os.path.join(target, "%(title).120B [%(id)s].%(ext)s")},
                "progress_hooks": [_progress_hook(callback)],
                "noplaylist": not playlist_items,
            }
        )
        if playlist_items:
            opts["playlist_items"] = ",".join(str(i) for i in playlist_items)
        langs = [s.strip() for s in (subtitle_langs or "").split(",") if s.strip()]
        if langs and index == 0:
            # 只写字幕文件，不内嵌（内嵌交给 ffmpeg-kit）
            opts["writesubtitles"] = True
            opts["writeautomaticsub"] = False
            opts["subtitleslangs"] = langs
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
        if info:
            title = title or (info.get("title") or "")
            duration = duration or int(info.get("duration") or 0)
        files = sorted(
            os.path.join(target, name)
            for name in os.listdir(target)
            if os.path.isfile(os.path.join(target, name))
        )
        groups.append(files)

    return json.dumps(
        {"groups": groups, "title": title, "duration": duration},
        ensure_ascii=False,
    )
