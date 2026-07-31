import { ROLES } from '../shared/roles';
import GuardLog from './GuardLog';
import Scanner from './Scanner';
import ScanResult from './ScanResult';
import ManualEntry from './ManualEntry';
import GateSwitch from './GateSwitch';

/**
 * Only the log lives inside the app shell.
 *
 * Scanner, verdict, manual lookup and gate selection are FULL-BLEED. A sidebar
 * on a scan result both wastes the screen a guard is squinting at outdoors and
 * gives them somewhere to wander mid-shift. The guard app is one screen deep
 * on purpose.
 */
export const guardRoutes = [
  { path: '/guard/log', element: <GuardLog />, allow: [ROLES.GUARD, ROLES.CAMPUS_ADMIN],
    nav: { label: 'Entry log', order: 60 } },
];

export const guardFullBleedRoutes = [
  { path: '/scan',         element: <Scanner />,    allow: [ROLES.GUARD] },
  { path: '/scan/result',  element: <ScanResult />, allow: [ROLES.GUARD] },
  { path: '/guard/manual', element: <ManualEntry />, allow: [ROLES.GUARD] },
  { path: '/guard/gate',   element: <GateSwitch />, allow: [ROLES.GUARD] },
];
