/* Scriptable-compatible API — Calendar, CalendarEvent, RecurrenceRule, Reminder, Contact, ContactsContainer, ContactsGroup, Photos. */
(function (g) {
  "use strict";

  const call = (fn, op, args) => fn(op, JSON.stringify(args ?? {}));
  const parse = (s) => (s == null ? null : JSON.parse(s));
  const ms = (d) => (d instanceof Date ? d.getTime() : d == null ? null : Number(d));
  const date = (t) => (t == null ? null : new Date(t));
  const ids = (list) => (list && list.length ? list.map((x) => x.identifier) : null);
  const DAY = 86400000;
  const startOfDay = (offsetDays = 0) => { const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() + offsetDays); return d; };
  /** Weeks start on Sunday, like Scriptable's default (US) calendar. */
  const startOfWeek = (offsetWeeks = 0) => { const d = startOfDay(); d.setDate(d.getDate() - d.getDay() + 7 * offsetWeeks); return d; };
  const plusDays = (d, n) => { const r = new Date(d.getTime()); r.setDate(r.getDate() + n); return r; };

  // ---------- Calendar ----------
  class Calendar {
    constructor(info, kind) {
      this.identifier = info.identifier; this.title = info.title; this.isSubscribed = !!info.isSubscribed;
      this.allowsContentModifications = info.allowsContentModifications !== false;
      this.color = info.color ? new Color(info.color) : Color.gray();
      this._kind = kind;
    }
    supportsAvailability() { return this._kind === "events"; }
    save() {
      if (this._kind !== "reminders") throw new Error("Only reminder lists can be saved");
      call(__reminders, "renameList", { identifier: this.identifier, title: this.title });
    }
    remove() {
      if (this._kind !== "reminders") throw new Error("Only reminder lists can be removed");
      call(__reminders, "removeList", { identifier: this.identifier });
    }
    static async forEvents() { return parse(call(__calendar, "calendars")).map((c) => new Calendar(c, "events")); }
    static async forReminders() { return parse(call(__reminders, "lists")).map((c) => new Calendar(c, "reminders")); }
    static async forEventsByTitle(title) {
      const c = (await Calendar.forEvents()).find((x) => x.title === title);
      if (!c) throw new Error(`No calendar named "${title}"`);
      return c;
    }
    static async forRemindersByTitle(title) {
      const c = (await Calendar.forReminders()).find((x) => x.title === title);
      if (!c) throw new Error(`No reminder list named "${title}"`);
      return c;
    }
    static async defaultForEvents() { return new Calendar(parse(call(__calendar, "default")), "events"); }
    static async defaultForReminders() { return new Calendar(parse(call(__reminders, "default")), "reminders"); }
    static async createForReminders(title) { return new Calendar(parse(call(__reminders, "createList", { title: String(title) })), "reminders"); }
    static async findOrCreateForReminders(title) {
      return (await Calendar.forReminders()).find((x) => x.title === title) ?? Calendar.createForReminders(title);
    }
    static async presentPicker() { throw new Error("Calendar.presentPicker() is not supported on Android"); }
  }
  g.Calendar = Calendar;

  // ---------- RecurrenceRule (RRULE) ----------
  const DAYS = ["SU", "MO", "TU", "WE", "TH", "FR", "SA"];
  const until = (d) => new Date(ms(d)).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");

  class RecurrenceRule {
    constructor(parts) { this._rrule = parts.filter(Boolean).join(";"); }
    static _make(freq, interval, opts = {}) {
      const list = (key, values, map = (v) => v) => (values && values.length ? `${key}=${values.map(map).join(",")}` : null);
      return new RecurrenceRule([
        `FREQ=${freq}`, `INTERVAL=${interval ?? 1}`,
        list("BYDAY", opts.days, (d) => DAYS[(Number(d) - 1 + 7) % 7]),
        list("BYMONTHDAY", opts.monthDays), list("BYMONTH", opts.months), list("BYWEEKNO", opts.weeks),
        list("BYYEARDAY", opts.yearDays), list("BYSETPOS", opts.positions),
        opts.endDate != null ? `UNTIL=${until(opts.endDate)}` : null,
        opts.count != null ? `COUNT=${opts.count}` : null,
      ]);
    }
  }
  for (const [name, freq] of [["daily", "DAILY"], ["weekly", "WEEKLY"], ["monthly", "MONTHLY"], ["yearly", "YEARLY"]]) {
    RecurrenceRule[name] = (interval) => RecurrenceRule._make(freq, interval);
    RecurrenceRule[`${name}EndDate`] = (interval, endDate) => RecurrenceRule._make(freq, interval, { endDate });
    RecurrenceRule[`${name}OccurrenceCount`] = (interval, count) => RecurrenceRule._make(freq, interval, { count });
  }
  const complex = {
    complexWeekly: ["WEEKLY", (a) => ({ days: a[0], positions: a[1] }), 2],
    complexMonthly: ["MONTHLY", (a) => ({ days: a[0], monthDays: a[1], positions: a[2] }), 3],
    complexYearly: ["YEARLY", (a) => ({ days: a[0], months: a[1], weeks: a[2], yearDays: a[3], positions: a[4] }), 5],
  };
  for (const [name, [freq, opts, n]] of Object.entries(complex)) {
    RecurrenceRule[name] = (interval, ...a) => RecurrenceRule._make(freq, interval, opts(a));
    RecurrenceRule[`${name}EndDate`] = (interval, ...a) => RecurrenceRule._make(freq, interval, { ...opts(a), endDate: a[n] });
    RecurrenceRule[`${name}OccurrenceCount`] = (interval, ...a) => RecurrenceRule._make(freq, interval, { ...opts(a), count: a[n] });
  }
  g.RecurrenceRule = RecurrenceRule;

  // ---------- CalendarEvent ----------
  class CalendarEvent {
    constructor(data, calendar) {
      const d = data ?? {};
      this.identifier = d.identifier ?? null; this.title = d.title ?? ""; this.location = d.location ?? null;
      this.notes = d.notes ?? null; this.startDate = date(d.start) ?? new Date(); this.endDate = date(d.end) ?? new Date(this.startDate.getTime() + 3600000);
      this.isAllDay = !!d.allDay; this.timeZone = d.timeZone ?? null; this.availability = d.availability ?? "busy";
      this.attendees = d.attendees ?? []; this.calendar = calendar ?? null; this._rrule = d.rrule ?? null;
    }
    addRecurrenceRule(rule) { this._rrule = rule._rrule; }
    removeAllRecurrenceRules() { this._rrule = null; }
    save() {
      if (!this.calendar) this.calendar = new Calendar(parse(call(__calendar, "default")), "events");
      this.identifier = call(__calendar, "save", {
        identifier: this.identifier, calendarId: this.calendar.identifier, title: String(this.title ?? ""),
        location: this.location, notes: this.notes, start: ms(this.startDate), end: ms(this.endDate), allDay: !!this.isAllDay,
        timeZone: this.timeZone, rrule: this._rrule, availability: this.availability,
      });
    }
    remove() { if (this.identifier) call(__calendar, "remove", { identifier: this.identifier }); }
    /** Opens the system calendar; changes made there are not reflected in this object. */
    async presentEdit() { call(__calendar, "edit", { identifier: this.identifier }); return this; }
    static async presentCreate() { call(__calendar, "edit", {}); return new CalendarEvent(); }
    static async between(startDate, endDate, calendars = []) {
      const cals = Object.fromEntries((await Calendar.forEvents()).map((c) => [c.identifier, c]));
      const events = parse(call(__calendar, "events", { start: ms(startDate), end: ms(endDate), ids: ids(calendars) }));
      return events.map((e) => new CalendarEvent(e, cals[e.calendarId] ?? null));
    }
    static today(calendars) { return CalendarEvent.between(startOfDay(0), startOfDay(1), calendars); }
    static tomorrow(calendars) { return CalendarEvent.between(startOfDay(1), startOfDay(2), calendars); }
    static yesterday(calendars) { return CalendarEvent.between(startOfDay(-1), startOfDay(0), calendars); }
    static thisWeek(calendars) { return CalendarEvent.between(startOfWeek(0), startOfWeek(1), calendars); }
    static nextWeek(calendars) { return CalendarEvent.between(startOfWeek(1), startOfWeek(2), calendars); }
    static lastWeek(calendars) { return CalendarEvent.between(startOfWeek(-1), startOfWeek(0), calendars); }
  }
  g.CalendarEvent = CalendarEvent;

  // ---------- Reminder (stored by the app) ----------
  class Reminder {
    constructor(data, calendar) {
      const d = data ?? {};
      this.identifier = d.identifier ?? null; this.title = d.title ?? ""; this.notes = d.notes ?? null;
      this.isCompleted = !!d.isCompleted; this.priority = d.priority ?? 0; this.dueDate = date(d.dueDate);
      this.dueDateIncludesTime = d.dueDateIncludesTime !== false; this.completionDate = date(d.completionDate);
      this.creationDate = date(d.creationDate); this.calendar = calendar ?? null; this._rrule = d.rrule ?? null;
    }
    get isOverdue() { return !this.isCompleted && this.dueDate != null && this.dueDate < new Date(); }
    addRecurrenceRule(rule) { this._rrule = rule._rrule; }
    removeAllRecurrenceRules() { this._rrule = null; }
    save() {
      this.identifier = call(__reminders, "save", {
        identifier: this.identifier, listId: this.calendar ? this.calendar.identifier : null, title: String(this.title ?? ""),
        notes: this.notes, isCompleted: !!this.isCompleted, completionDate: ms(this.completionDate), creationDate: ms(this.creationDate),
        dueDate: ms(this.dueDate), dueDateIncludesTime: !!this.dueDateIncludesTime, priority: Number(this.priority) || 0, rrule: this._rrule,
      });
    }
    remove() { if (this.identifier) call(__reminders, "remove", { identifier: this.identifier }); }
    static async all(calendars = []) {
      const lists = Object.fromEntries((await Calendar.forReminders()).map((c) => [c.identifier, c]));
      return parse(call(__reminders, "all", { ids: ids(calendars) })).map((r) => new Reminder(r, lists[r.listId] ?? null));
    }
    static async allCompleted(calendars) { return (await Reminder.all(calendars)).filter((r) => r.isCompleted); }
    static async allIncomplete(calendars) { return (await Reminder.all(calendars)).filter((r) => !r.isCompleted); }
    static async scheduled(calendars) { return (await Reminder.all(calendars)).filter((r) => r.dueDate != null && !r.isCompleted); }
    static async allDueBetween(start, end, calendars) {
      return (await Reminder.all(calendars)).filter((r) => r.dueDate != null && r.dueDate >= start && r.dueDate < end);
    }
    static async completedDueBetween(start, end, calendars) { return (await Reminder.allDueBetween(start, end, calendars)).filter((r) => r.isCompleted); }
    static async incompleteDueBetween(start, end, calendars) { return (await Reminder.allDueBetween(start, end, calendars)).filter((r) => !r.isCompleted); }
    static async completedBetween(start, end, calendars) {
      return (await Reminder.all(calendars)).filter((r) => r.completionDate != null && r.completionDate >= start && r.completionDate < end);
    }
  }
  const ranges = {
    Today: () => [startOfDay(0), startOfDay(1)], Tomorrow: () => [startOfDay(1), startOfDay(2)],
    Yesterday: () => [startOfDay(-1), startOfDay(0)], ThisWeek: () => [startOfWeek(0), startOfWeek(1)],
    NextWeek: () => [startOfWeek(1), startOfWeek(2)], LastWeek: () => [startOfWeek(-1), startOfWeek(0)],
  };
  for (const [suffix, range] of Object.entries(ranges)) {
    Reminder[`allDue${suffix}`] = (calendars) => Reminder.allDueBetween(...range(), calendars);
    Reminder[`completedDue${suffix}`] = (calendars) => Reminder.completedDueBetween(...range(), calendars);
    Reminder[`incompleteDue${suffix}`] = (calendars) => Reminder.incompleteDueBetween(...range(), calendars);
    Reminder[`completed${suffix}`] = (calendars) => Reminder.completedBetween(...range(), calendars);
  }
  g.Reminder = Reminder;

  // ---------- Contacts ----------
  const labeled = (list) => (list ?? []).map((v) => ({ label: v.label ?? null, localizedLabel: v.label ?? null, value: String(v.value), identifier: v.identifier ?? null }));
  const addresses = (list) => (list ?? []).map((a) => ({ ...a, localizedLabel: a.label ?? null }));
  const pending = { adds: [], updates: [], deletes: [], groupAdds: [], groupUpdates: [], groupDeletes: [], memberAdds: [], memberRemoves: [] };

  class Contact {
    constructor(data) {
      const d = data ?? {};
      this.identifier = d.identifier ?? null; this._containerId = d.containerId ?? null;
      for (const k of ["namePrefix", "givenName", "middleName", "familyName", "nameSuffix", "nickname", "organizationName", "departmentName", "jobTitle", "note"]) this[k] = d[k] ?? "";
      this.birthday = date(d.birthday);
      this.phoneNumbers = labeled(d.phoneNumbers); this.emailAddresses = labeled(d.emailAddresses);
      this.urlAddresses = labeled(d.urlAddresses); this.postalAddresses = addresses(d.postalAddresses);
      this.isImageAvailable = !!d.hasImage; this._image = undefined;
      for (const k of ["Identifier", "NamePrefix", "GivenName", "MiddleName", "FamilyName", "NameSuffix", "Nickname", "Birthday",
        "PhoneNumbers", "EmailAddresses", "PostalAddresses", "SocialProfiles", "Image", "Note", "URLAddresses",
        "OrganizationName", "DepartmentName", "JobTitle", "Dates"]) this[`is${k}Available`] ??= true;
      this.isImageAvailable = !!d.hasImage;
    }
    get image() {
      if (this._image === undefined) {
        const info = this.identifier && this.isImageAvailable ? parse(call(__contacts, "image", { identifier: this.identifier })) : null;
        this._image = info ? new Image(info) : null;
      }
      return this._image;
    }
    _data(containerId) {
      const strip = (list) => list.map(({ localizedLabel, ...rest }) => rest);
      return {
        identifier: this.identifier, containerId: containerId ?? this._containerId,
        namePrefix: this.namePrefix, givenName: this.givenName, middleName: this.middleName, familyName: this.familyName,
        nameSuffix: this.nameSuffix, nickname: this.nickname, organizationName: this.organizationName,
        departmentName: this.departmentName, jobTitle: this.jobTitle, note: this.note, birthday: ms(this.birthday),
        phoneNumbers: strip(labeled(this.phoneNumbers)), emailAddresses: strip(labeled(this.emailAddresses)),
        urlAddresses: strip(labeled(this.urlAddresses)), postalAddresses: strip(addresses(this.postalAddresses)),
      };
    }
    static async all(containers) { return parse(call(__contacts, "all", { ids: ids(containers) })).map((c) => new Contact(c)); }
    static async inGroups(groups) { return parse(call(__contacts, "inGroups", { ids: ids(groups) ?? [] })).map((c) => new Contact(c)); }
    static add(contact, containerIdentifier) {
      const container = containerIdentifier ?? parse(call(__contacts, "defaultContainer")).identifier;
      pending.adds.push(contact._data(container));
    }
    static update(contact) { pending.updates.push(contact._data()); }
    static delete(contact) { if (contact.identifier) pending.deletes.push(contact.identifier); }
    static async persistChanges() {
      const changes = JSON.parse(JSON.stringify(pending));
      for (const k of Object.keys(pending)) pending[k] = [];
      call(__contacts, "apply", changes);
    }
  }
  g.Contact = Contact;

  class ContactsContainer {
    constructor(info) { this.identifier = info.identifier; this.name = info.name; }
    static async default() { return new ContactsContainer(parse(call(__contacts, "defaultContainer"))); }
    static async all() { return parse(call(__contacts, "containers")).map((c) => new ContactsContainer(c)); }
    static async withIdentifier(identifier) {
      const c = (await ContactsContainer.all()).find((x) => x.identifier === identifier);
      if (!c) throw new Error(`No contacts container "${identifier}"`);
      return c;
    }
  }
  g.ContactsContainer = ContactsContainer;

  class ContactsGroup {
    constructor(info) { this.identifier = info?.identifier ?? null; this.name = info?.name ?? ""; this._containerId = info?.containerId ?? null; }
    addMember(contact) { pending.memberAdds.push({ contactId: contact.identifier, groupId: this.identifier }); }
    removeMember(contact) { pending.memberRemoves.push({ contactId: contact.identifier, groupId: this.identifier }); }
    static async all(containers) { return parse(call(__contacts, "groups", { ids: ids(containers) })).map((x) => new ContactsGroup(x)); }
    static add(group, containerIdentifier) {
      const container = containerIdentifier ?? parse(call(__contacts, "defaultContainer")).identifier;
      pending.groupAdds.push({ name: String(group.name), containerId: container });
    }
    static update(group) { pending.groupUpdates.push({ identifier: group.identifier, name: String(group.name), containerId: group._containerId }); }
    static delete(group) { if (group.identifier) pending.groupDeletes.push(group.identifier); }
  }
  g.ContactsGroup = ContactsGroup;

  // ---------- Photos ----------
  const latest = (count, screenshots) => parse(call(__photos, "latest", { count, screenshots })).map((i) => new Image(i));
  const pick = async (source) => {
    const info = await __photosPick(source);
    if (info == null) throw new Error("Photo selection was cancelled");
    return new Image(JSON.parse(info));
  };
  const unsupported = (name) => async () => { throw new Error(`Photos.${name}() is not supported on Android`); };

  g.Photos = {
    fromLibrary: () => pick("library"),
    fromCamera: () => pick("camera"),
    async latestPhoto() { const [p] = latest(1, false); if (!p) throw new Error("No photos"); return p; },
    async latestPhotos(count) { return latest(Number(count) || 1, false); },
    async latestScreenshot() { const [p] = latest(1, true); if (!p) throw new Error("No screenshots"); return p; },
    async latestScreenshots(count) { return latest(Number(count) || 1, true); },
    save(image) {
      if (!(image instanceof Image)) throw new Error("Photos.save() expects an Image");
      call(__photos, "save", { src: image._src });
    },
    removeLatestPhoto: unsupported("removeLatestPhoto"),
    removeLatestPhotos: unsupported("removeLatestPhotos"),
    removeLatestScreenshot: unsupported("removeLatestScreenshot"),
    removeLatestScreenshots: unsupported("removeLatestScreenshots"),
  };
})(globalThis);
