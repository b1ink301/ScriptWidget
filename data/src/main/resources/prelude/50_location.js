/* Scriptable-compatible API — Location. */
(function (g) {
  "use strict";

  let accuracy = "Best";

  g.Location = {
    async current() {
      const l = JSON.parse(await __location("current", accuracy));
      return {
        latitude: l.latitude, longitude: l.longitude, altitude: l.altitude ?? null,
        horizontalAccuracy: l.horizontalAccuracy ?? null, verticalAccuracy: l.verticalAccuracy ?? null,
        timestamp: new Date(l.timestamp),
      };
    },
    setAccuracyToBest() { accuracy = "Best"; },
    setAccuracyToTenMeters() { accuracy = "TenMeters"; },
    setAccuracyToHundredMeters() { accuracy = "HundredMeters"; },
    setAccuracyToKilometer() { accuracy = "Kilometer"; },
    setAccuracyToThreeKilometers() { accuracy = "ThreeKilometers"; },
    async reverseGeocode(latitude, longitude, locale) {
      const request = JSON.stringify({ lat: Number(latitude), lon: Number(longitude), locale: locale ?? null });
      return JSON.parse(await __location("reverse", request));
    },
  };
})(globalThis);
