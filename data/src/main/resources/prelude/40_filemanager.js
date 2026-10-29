/* Scriptable-compatible API — FileManager (sandboxed to the app's script storage). */
(function (g) {
  "use strict";

  const [documents, caches, tmp, bookmarks] = JSON.parse(__fs("dirs"));
  const baseName = (p) => String(p).split("/").filter(Boolean).pop() || "";

  class FileManager {
    static local() { return new FileManager(); }
    /** No iCloud on Android: same storage as local(). */
    static iCloud() { return new FileManager(); }

    documentsDirectory() { return documents; }
    libraryDirectory() { return documents; }
    cacheDirectory() { return caches; }
    temporaryDirectory() { return tmp; }

    joinPath(lhs, rhs) { return String(lhs).replace(/\/+$/, "") + "/" + String(rhs).replace(/^\/+/, ""); }
    fileName(path, includeFileExtension = false) {
      const name = baseName(path);
      if (includeFileExtension) return name;
      const dot = name.lastIndexOf(".");
      return dot > 0 ? name.slice(0, dot) : name;
    }
    fileExtension(path) {
      const name = baseName(path);
      const dot = name.lastIndexOf(".");
      return dot > 0 ? name.slice(dot + 1) : "";
    }

    fileExists(path) { return __fs("exists", path); }
    isDirectory(path) { return __fs("isDirectory", path); }
    createDirectory(path, intermediateDirectories = false) { __fs("createDirectory", path, !!intermediateDirectories); }
    listContents(path) { return JSON.parse(__fs("list", path)); }
    remove(path) { __fs("remove", path); }

    /** Returns null when the file doesn't exist. */
    readString(path) { return __fs("readString", path); }
    writeString(path, content) { __fs("writeString", path, String(content)); }

    readImage(path) {
      const info = __fs("readImage", path);
      return info == null ? null : new Image(JSON.parse(info));
    }
    writeImage(path, image) {
      if (!(image instanceof Image)) throw new Error("writeImage() expects an Image");
      __fs("writeImage", path, image._src);
    }

    modificationDate(path) { const t = __fs("modificationDate", path); return t == null ? null : new Date(t); }
    creationDate(path) { return this.modificationDate(path); }
    /** Size in kilobytes, like Scriptable. */
    fileSize(path) { const b = __fs("size", path); return b == null ? null : b / 1024; }

    // File bookmarks: files the user picked in ScriptWidget (목록 → 메뉴 → 파일 북마크).
    bookmarkExists(name) { return !!bookmarks && __fs("exists", this.joinPath(bookmarks, String(name))); }
    bookmarkedPath(name) {
      if (!this.bookmarkExists(name)) {
        throw new Error(`No file bookmark named "${name}". Add it in ScriptWidget: 메뉴 → 파일 북마크.`);
      }
      return this.joinPath(bookmarks, String(name));
    }
    allFileBookmarks() {
      if (!bookmarks || !__fs("exists", bookmarks)) return [];
      return this.listContents(bookmarks).filter((n) => !n.startsWith(".")).map((name) => ({ name, source: "host" }));
    }

    isFileStoredIniCloud() { return false; }
    isFileDownloaded() { return true; }
    async downloadFileFromiCloud() {}
  }
  g.FileManager = FileManager;
})(globalThis);
