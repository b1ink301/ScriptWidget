/* Scriptable-compatible API — Device (a snapshot taken when first used in a run). */
(function (g) {
  "use strict";

  let snapshot = null;
  const d = () => (snapshot ??= JSON.parse(__device()));

  g.Device = {
    name: () => d().name,
    systemName: () => d().systemName,
    systemVersion: () => d().systemVersion,
    model: () => d().model,
    isPhone: () => d().isPhone,
    isPad: () => d().isPad,
    screenSize: () => new Size(d().screenWidth, d().screenHeight),
    screenResolution: () => new Size(Math.round(d().screenWidth * d().screenScale), Math.round(d().screenHeight * d().screenScale)),
    screenScale: () => d().screenScale,
    screenBrightness: () => d().screenBrightness,
    isInPortrait: () => d().orientation === "portrait",
    isInPortraitUpsideDown: () => false,
    isInLandscapeLeft: () => d().orientation === "landscapeLeft",
    isInLandscapeRight: () => d().orientation === "landscapeRight",
    isFaceUp: () => false,
    isFaceDown: () => false,
    batteryLevel: () => d().batteryLevel,
    isDischarging: () => !d().isCharging,
    isCharging: () => d().isCharging,
    isFullyCharged: () => d().isFullyCharged,
    preferredLanguages: () => d().preferredLanguages.slice(),
    locale: () => d().locale,
    language: () => d().locale.split(/[_-]/)[0],
    isUsingDarkAppearance: () => d().isUsingDarkAppearance,
    volume: () => d().volume,
    setScreenBrightness: () => console.warn("Device.setScreenBrightness() is not supported on Android"),
  };
})(globalThis);
