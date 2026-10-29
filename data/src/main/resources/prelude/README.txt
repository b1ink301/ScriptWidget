* Scriptable-compatible API subset, evaluated before every user script.
 *
 * Native bindings (defined in ScriptRunner.kt):
 *   __log(level, message)          console output
 *   __setWidget(json)              receives the serialized widget tree
 *   __sleep(ms)            async   timer backend
 *   __http(specJson)       async   -> response JSON string
 *   __loadImage(specJson)  async   -> {src, width, height} JSON string
 *   __env                          {runsInWidget, widgetFamily, widgetParameter}

Files listed in index.txt are concatenated in order; each file is an IIFE that
publishes its API on globalThis.
