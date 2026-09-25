/** Leaves the SPA for an external page (the hosted payment page). A module of its own so tests can replace it. */
export function redirectTo(url: string): void {
  window.location.assign(url);
}
