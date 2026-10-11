import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ICONS_DIR = path.resolve(__dirname, '../src/main/resources/icons');

const LIGHT_COLOR = '#6C707E';
const DARK_COLOR = '#CED0D6';

// Paths:
const DEFAULT_PATH = 'M4 5h3v1h-3zM17 5h3v1h-3zM3 6h1v1h-1zM7 6h1v1h-1zM16 6h1v1h-1zM20 6h1v1h-1zM2 7h1v5h-1zM7 7h10v1h-10zM21 7h1v5h-1zM8 9h1v1h-1zM10 9h1v1h-1zM13 9h1v1h-1zM15 9h1v1h-1zM5 10h1v1h-1zM18 10h1v1h-1zM6 11h2v2h-2zM16 11h2v2h-2zM1 12h1v1h-1zM22 12h1v1h-1zM2 13h1v1h-1zM11 13h2v1h-2zM21 13h1v1h-1zM1 14h1v1h-1zM4 14h1v3h-1zM6 14h1v3h-1zM17 14h1v3h-1zM19 14h1v3h-1zM22 14h1v1h-1zM2 15h1v2h-1zM21 15h1v2h-1zM3 17h1v1h-1zM20 17h1v1h-1zM4 18h16v1h-16z';

const WORK_BASE = 'M4 5h3v1h-3zM17 5h3v1h-3zM3 6h1v1h-1zM7 6h1v1h-1zM16 6h1v1h-1zM20 6h1v1h-1zM2 7h1v5h-1zM7 7h10v1h-10zM21 7h1v5h-1zM8 9h1v1h-1zM10 9h1v1h-1zM13 9h1v1h-1zM15 9h1v1h-1zM1 12h1v1h-1zM22 12h1v1h-1zM2 13h1v1h-1zM11 13h2v1h-2zM21 13h1v1h-1zM1 14h1v1h-1zM4 14h1v3h-1zM6 14h1v3h-1zM17 14h1v3h-1zM19 14h1v3h-1zM22 14h1v1h-1zM2 15h1v2h-1zM21 15h1v2h-1zM3 17h1v1h-1zM20 17h1v1h-1zM4 18h16v1h-16zM4 23h3v1h-3zM8 23h2v1h-2zM11 23h2v1h-2zM14 23h2v1h-2zM17 23h3v1h-3z';
const WORK_EYES_CENTER = 'M5 10h1v1h-1zM18 10h1v1h-1zM6 11h2v2h-2zM16 11h2v2h-2z';
const WORK_EYES_RIGHT  = 'M6 10h1v1h-1zM19 10h1v1h-1zM7 11h2v2h-2zM17 11h2v2h-2z';
const WORK_EYES_LEFT   = 'M4 10h1v1h-1zM17 10h1v1h-1zM5 11h2v2h-2zM15 11h2v2h-2z';
const WORK_PAWS_A      = 'M15 20h2v1h-2zM7 21h2v1h-2zM14 21h4v1h-4zM6 22h4v1h-4z';
const WORK_PAWS_B      = 'M7 20h2v1h-2zM6 21h4v1h-4zM15 21h2v1h-2zM14 22h4v1h-4z';
const WORK_SWEAT_3     = 'M8 1h2v2h-2zM11 1h2v2h-2zM14 1h2v2h-2z';
const WORK_SWEAT_1     = 'M8 1h2v2h-2z';
const WORK_SWEAT_2     = 'M8 1h2v2h-2zM11 1h2v2h-2z';

const WORK_FRAMES_PATHS = [
  [WORK_BASE, WORK_EYES_CENTER, WORK_PAWS_A],
  [WORK_BASE, WORK_EYES_CENTER, WORK_PAWS_B],
  [WORK_BASE, WORK_EYES_RIGHT,  WORK_PAWS_A, WORK_SWEAT_1],
  [WORK_BASE, WORK_EYES_RIGHT,  WORK_PAWS_B, WORK_SWEAT_1],
  [WORK_BASE, WORK_EYES_CENTER, WORK_PAWS_A, WORK_SWEAT_2],
  [WORK_BASE, WORK_EYES_CENTER, WORK_PAWS_B, WORK_SWEAT_2],
  [WORK_BASE, WORK_EYES_LEFT,   WORK_PAWS_A, WORK_SWEAT_3],
  [WORK_BASE, WORK_EYES_LEFT,   WORK_PAWS_B, WORK_SWEAT_3],
];

const HELP_CAT_CENTER_A = 'M4 5h3v1h-3zM17 5h3v1h-3zM3 6h1v1h-1zM7 6h1v1h-1zM16 6h1v1h-1zM20 6h1v1h-1zM2 7h1v5h-1zM7 7h10v1h-10zM21 7h1v5h-1zM8 9h1v1h-1zM10 9h1v1h-1zM13 9h1v1h-1zM15 9h1v1h-1zM6 10h2v3h-2zM16 10h2v3h-2zM1 12h1v1h-1zM22 12h1v1h-1zM2 13h1v1h-1zM11 13h2v1h-2zM21 13h1v1h-1zM1 14h1v1h-1zM4 14h1v3h-1zM6 14h1v3h-1zM17 14h1v3h-1zM19 14h1v3h-1zM22 14h1v1h-1zM2 15h1v2h-1zM11 15h2v2h-2zM21 15h1v2h-1zM3 17h1v1h-1zM20 17h1v1h-1zM4 18h16v1h-16z';
const HELP_CAT_RIGHT    = 'M5 5h3v1h-3zM18 5h3v1h-3zM4 6h1v1h-1zM8 6h1v1h-1zM17 6h1v1h-1zM21 6h1v1h-1zM3 7h1v5h-1zM8 7h10v1h-10zM22 7h1v5h-1zM9 9h1v1h-1zM11 9h1v1h-1zM14 9h1v1h-1zM16 9h1v1h-1zM7 10h2v3h-2zM17 10h2v3h-2zM2 12h1v1h-1zM23 12h1v1h-1zM3 13h1v1h-1zM12 13h2v1h-2zM22 13h1v1h-1zM2 14h1v1h-1zM5 14h1v3h-1zM7 14h1v3h-1zM18 14h1v3h-1zM20 14h1v3h-1zM23 14h1v1h-1zM3 15h1v2h-1zM12 15h2v2h-2zM22 15h1v2h-1zM4 17h1v1h-1zM21 17h1v1h-1zM5 18h16v1h-16z';
const HELP_CAT_CENTER_B = 'M4 5h3v1h-3zM17 5h3v1h-3zM3 6h1v1h-1zM7 6h1v1h-1zM16 6h1v1h-1zM20 6h1v1h-1zM2 7h1v5h-1zM7 7h10v1h-10zM21 7h1v5h-1zM8 9h1v1h-1zM10 9h1v1h-1zM13 9h1v1h-1zM15 9h1v1h-1zM6 10h2v3h-2zM16 10h2v3h-2zM1 12h1v1h-1zM22 12h1v1h-1zM2 13h1v1h-1zM11 13h2v1h-2zM21 13h1v1h-1zM1 14h1v1h-1zM4 14h1v3h-1zM6 14h1v3h-1zM17 14h1v3h-1zM19 14h1v3h-1zM22 14h1v1h-1zM2 15h1v2h-1zM11 15h2v1h-2zM21 15h1v2h-1zM3 17h1v1h-1zM20 17h1v1h-1zM4 18h16v1h-16z';
const HELP_CAT_LEFT     = 'M3 5h3v1h-3zM16 5h3v1h-3zM2 6h1v1h-1zM6 6h1v1h-1zM15 6h1v1h-1zM19 6h1v1h-1zM1 7h1v5h-1zM6 7h10v1h-10zM20 7h1v5h-1zM7 9h1v1h-1zM9 9h1v1h-1zM12 9h1v1h-1zM14 9h1v1h-1zM5 10h2v3h-2zM15 10h2v3h-2zM0 12h1v1h-1zM21 12h1v1h-1zM1 13h1v1h-1zM10 13h2v1h-2zM20 13h1v1h-1zM0 14h1v1h-1zM3 14h1v3h-1zM5 14h1v3h-1zM16 14h1v3h-1zM18 14h1v3h-1zM21 14h1v1h-1zM1 15h1v2h-1zM10 15h2v1h-2zM20 15h1v2h-1zM2 17h1v1h-1zM19 17h1v1h-1zM3 18h16v1h-16z';
const HELP_EXCLAMATION  = 'M11 0h2v3h-2zM11 4h2v2h-2z';

const HELP_FRAMES_PATHS = [
  [HELP_CAT_CENTER_A, HELP_EXCLAMATION],
  [HELP_CAT_RIGHT,    HELP_EXCLAMATION],
  [HELP_CAT_CENTER_B, HELP_EXCLAMATION],
  [HELP_CAT_LEFT,     HELP_EXCLAMATION],
  [HELP_CAT_CENTER_A, HELP_EXCLAMATION],
  [HELP_CAT_RIGHT,    HELP_EXCLAMATION],
  [HELP_CAT_CENTER_B],
  [HELP_CAT_LEFT],
];

function buildSvg({ paths, color, size = 20, viewBox = '0 0 24 24', label = 'Manul' }) {
  const combinedPath = paths.join('');
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="${viewBox}" shape-rendering="crispEdges" role="img" aria-label="${label}">
  <path fill="${color}" d="${combinedPath}"/>
</svg>
`;
}

function buildAnimatedWorkingSvg({ color, darkColor = null, size = 20 }) {
  const isDark = darkColor === null;
  const darkQuery = isDark ? '' : `@media(prefers-color-scheme:dark){.mn{color:${darkColor}}}`;

  return `<svg xmlns="http://www.w3.org/2000/svg" class="mn mn-work" color="${color}" viewBox="0 0 24 24" width="${size}" height="${size}" shape-rendering="crispEdges" role="img" aria-label="Manul pracuje">
<title>Manul pracuje</title>
<style>${darkQuery}.mn-work .mn-work0{animation:mn-work0 1.6s steps(1,end) infinite}@keyframes mn-work0{0%{opacity:1}25%{opacity:0}50%{opacity:1}75%{opacity:0}}.mn-work .mn-work1{animation:mn-work1 1.6s steps(1,end) infinite}@keyframes mn-work1{0%{opacity:0}25%{opacity:1}50%{opacity:0}}.mn-work .mn-work2{animation:mn-work2 1.6s steps(1,end) infinite}@keyframes mn-work2{0%{opacity:0}75%{opacity:1}}.mn-work .mn-work3{animation:mn-work3 1.6s steps(1,end) infinite}@keyframes mn-work3{0%{opacity:1}12.5%{opacity:0}25%{opacity:1}37.5%{opacity:0}50%{opacity:1}62.5%{opacity:0}75%{opacity:1}87.5%{opacity:0}}.mn-work .mn-work4{animation:mn-work4 1.6s steps(1,end) infinite}@keyframes mn-work4{0%{opacity:0}12.5%{opacity:1}25%{opacity:0}37.5%{opacity:1}50%{opacity:0}62.5%{opacity:1}75%{opacity:0}87.5%{opacity:1}}.mn-work .mn-work5{animation:mn-work5 1.6s steps(1,end) infinite}@keyframes mn-work5{0%{opacity:0}75%{opacity:1}}.mn-work .mn-work6{animation:mn-work6 1.6s steps(1,end) infinite}@keyframes mn-work6{0%{opacity:0}25%{opacity:1}50%{opacity:0}}.mn-work .mn-work7{animation:mn-work7 1.6s steps(1,end) infinite}@keyframes mn-work7{0%{opacity:0}50%{opacity:1}75%{opacity:0}}@media(prefers-reduced-motion:reduce){.mn-work path{animation:none!important}}</style>
<path fill="currentColor" d="${WORK_BASE}"/>
<path class="mn-work0" fill="currentColor" d="${WORK_EYES_CENTER}"/>
<path class="mn-work1" opacity="0" fill="currentColor" d="${WORK_EYES_RIGHT}"/>
<path class="mn-work2" opacity="0" fill="currentColor" d="${WORK_EYES_LEFT}"/>
<path class="mn-work3" fill="currentColor" d="${WORK_PAWS_A}"/>
<path class="mn-work4" opacity="0" fill="currentColor" d="${WORK_PAWS_B}"/>
<path class="mn-work5" fill="currentColor" d="${WORK_SWEAT_3}"/>
<path class="mn-work6" opacity="0" fill="currentColor" d="${WORK_SWEAT_1}"/>
<path class="mn-work7" opacity="0" fill="currentColor" d="${WORK_SWEAT_2}"/>
</svg>
`;
}

function buildAnimatedHelpSvg({ color, darkColor = null, size = 20 }) {
  const isDark = darkColor === null;
  const darkQuery = isDark ? '' : `@media(prefers-color-scheme:dark){.mn{color:${darkColor}}}`;

  return `<svg xmlns="http://www.w3.org/2000/svg" class="mn mn-help" color="${color}" viewBox="0 0 24 24" width="${size}" height="${size}" shape-rendering="crispEdges" role="img" aria-label="Manul potrzebuje pomocy">
<title>Manul potrzebuje pomocy</title>
<style>${darkQuery}.mn-help .mn-help0{animation:mn-help0 0.96s steps(1,end) infinite}@keyframes mn-help0{0%{opacity:1}12.5%{opacity:0}50%{opacity:1}62.5%{opacity:0}}.mn-help .mn-help1{animation:mn-help1 0.96s steps(1,end) infinite}@keyframes mn-help1{0%{opacity:0}12.5%{opacity:1}25%{opacity:0}62.5%{opacity:1}75%{opacity:0}}.mn-help .mn-help2{animation:mn-help2 0.96s steps(1,end) infinite}@keyframes mn-help2{0%{opacity:0}25%{opacity:1}37.5%{opacity:0}75%{opacity:1}87.5%{opacity:0}}.mn-help .mn-help3{animation:mn-help3 0.96s steps(1,end) infinite}@keyframes mn-help3{0%{opacity:0}37.5%{opacity:1}50%{opacity:0}87.5%{opacity:1}}.mn-help .mn-help4{animation:mn-help4 0.96s steps(1,end) infinite}@keyframes mn-help4{0%{opacity:1}75%{opacity:0}}@media(prefers-reduced-motion:reduce){.mn-help path{animation:none!important}}</style>
<path class="mn-help0" fill="currentColor" d="${HELP_CAT_CENTER_A}"/>
<path class="mn-help1" opacity="0" fill="currentColor" d="${HELP_CAT_RIGHT}"/>
<path class="mn-help2" opacity="0" fill="currentColor" d="${HELP_CAT_CENTER_B}"/>
<path class="mn-help3" opacity="0" fill="currentColor" d="${HELP_CAT_LEFT}"/>
<path class="mn-help4" fill="currentColor" d="${HELP_EXCLAMATION}"/>
</svg>
`;
}

function writeIconVariants(baseName, paths, label, viewBox = '0 0 24 24') {
  const configs = [
    { file: `${baseName}.svg`, color: LIGHT_COLOR, size: 20 },
    { file: `${baseName}_dark.svg`, color: DARK_COLOR, size: 20 },
    { file: `${baseName}@20x20.svg`, color: LIGHT_COLOR, size: 20 },
    { file: `${baseName}@20x20_dark.svg`, color: DARK_COLOR, size: 20 },
  ];

  for (const cfg of configs) {
    const content = buildSvg({
      paths,
      color: cfg.color,
      size: cfg.size,
      viewBox,
      label,
    });
    const targetPath = path.join(ICONS_DIR, cfg.file);
    fs.writeFileSync(targetPath, content, 'utf8');
  }
}

// 1. Default / Idle icon (manul-default and harnessAside)
writeIconVariants('manul-default', [DEFAULT_PATH], 'Manul — domyślne logo');
writeIconVariants('harnessAside', [DEFAULT_PATH], 'Harness Aside');

// 2. Fully animated SVGs (retaining all CSS @keyframes animations)
fs.writeFileSync(path.join(ICONS_DIR, 'manul-working.svg'), buildAnimatedWorkingSvg({ color: LIGHT_COLOR, darkColor: DARK_COLOR, size: 20 }));
fs.writeFileSync(path.join(ICONS_DIR, 'manul-working_dark.svg'), buildAnimatedWorkingSvg({ color: DARK_COLOR, size: 20 }));
fs.writeFileSync(path.join(ICONS_DIR, 'manul-working@20x20.svg'), buildAnimatedWorkingSvg({ color: LIGHT_COLOR, darkColor: DARK_COLOR, size: 20 }));
fs.writeFileSync(path.join(ICONS_DIR, 'manul-working@20x20_dark.svg'), buildAnimatedWorkingSvg({ color: DARK_COLOR, size: 20 }));

fs.writeFileSync(path.join(ICONS_DIR, 'manul-help.svg'), buildAnimatedHelpSvg({ color: LIGHT_COLOR, darkColor: DARK_COLOR, size: 20 }));
fs.writeFileSync(path.join(ICONS_DIR, 'manul-help_dark.svg'), buildAnimatedHelpSvg({ color: DARK_COLOR, size: 20 }));
fs.writeFileSync(path.join(ICONS_DIR, 'manul-help@20x20.svg'), buildAnimatedHelpSvg({ color: LIGHT_COLOR, darkColor: DARK_COLOR, size: 20 }));
fs.writeFileSync(path.join(ICONS_DIR, 'manul-help@20x20_dark.svg'), buildAnimatedHelpSvg({ color: DARK_COLOR, size: 20 }));

// 3. Frame-by-frame SVGs (00..07) for IntelliJ Swing runtime timer
for (let i = 0; i < 8; i++) {
  const pad = String(i).padStart(2, '0');
  writeIconVariants(`manul_work_${pad}`, WORK_FRAMES_PATHS[i], `Manul pracuje (klatka ${i})`);
}

for (let i = 0; i < 8; i++) {
  const pad = String(i).padStart(2, '0');
  writeIconVariants(`manul_help_${pad}`, HELP_FRAMES_PATHS[i], `Manul potrzebuje pomocy (klatka ${i})`);
}

console.log('Successfully generated all Manul SVG icons (including full CSS @keyframes animations in main SVGs)!');
