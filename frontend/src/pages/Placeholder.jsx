/**
 * What every teammate replaces on Day 14.
 *
 * Exists so routing, guards and layout are provably working before anyone has
 * written a real screen - and so a missing screen looks deliberate rather than
 * like a broken build.
 */
export default function Placeholder({ title, owner, screens }) {
  return (
    <div>
      <h1>{title}</h1>
      <p className="muted">
        Owned by {owner}. Screens {screens}.
      </p>
      <p>
        Replace this component. The shell already gives you the token, the role
        guard and the API client - import your service from <code>api/client.js</code>{' '}
        and the response is unwrapped for you.
      </p>
    </div>
  );
}
