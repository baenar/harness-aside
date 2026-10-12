import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ICONS_DIR = path.resolve(__dirname, '../src/main/resources/icons');

const LIGHT_COLOR = '#6C707E';
const DARK_COLOR = '#CED0D6';

// Read user's v3 source files as templates:
const defaultV3Raw = fs.readFileSync(path.join(ICONS_DIR, 'manul-default-v3.svg'), 'utf8');
const workingV3Raw = fs.readFileSync(path.join(ICONS_DIR, 'manul-working-v3.svg'), 'utf8');
const helpV3Raw = fs.readFileSync(path.join(ICONS_DIR, 'manul-help-v3.svg'), 'utf8');

/**
 * Transforms an SVG to use explicit palette hex colors (#6C707E or #CED0D6)
 * instead of `currentColor` for stroke and fill attributes.
 * This is CRITICAL for IntelliJ Platform's `SvgAttributePatcher`:
 * IntelliJ's selection patcher matches explicit hex values (`#6C707E` / `#CED0D6`)
 * and replaces them with `#FFFFFF` when a tool window button is selected/active (blue tab).
 */
function applyPaletteColors(svgContent, color) {
  return svgContent
    .replace(/stroke="currentColor"/g, `stroke="${color}"`)
    .replace(/fill="currentColor"/g, `fill="${color}"`)
    .replace(/color="[^"]*"/g, `color="${color}"`)
    .replace(/width="[^"]+"/, 'width="20"')
    .replace(/height="[^"]+"/, 'height="20"');
}

// 1. Write manul-default and harnessAside
const defaultLight = applyPaletteColors(defaultV3Raw, LIGHT_COLOR);
const defaultDark = applyPaletteColors(defaultV3Raw, DARK_COLOR);

fs.writeFileSync(path.join(ICONS_DIR, 'manul-default.svg'), defaultLight, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-default_dark.svg'), defaultDark, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-default@20x20.svg'), defaultLight, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-default@20x20_dark.svg'), defaultDark, 'utf8');

fs.writeFileSync(path.join(ICONS_DIR, 'harnessAside.svg'), defaultLight, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'harnessAside_dark.svg'), defaultDark, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'harnessAside@20x20.svg'), defaultLight, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'harnessAside@20x20_dark.svg'), defaultDark, 'utf8');

// 2. Write manul-working from manul-working-v3 (fully animated CSS SVG)
const workingLight = applyPaletteColors(workingV3Raw, LIGHT_COLOR);
const workingDark = applyPaletteColors(workingV3Raw, DARK_COLOR);

fs.writeFileSync(path.join(ICONS_DIR, 'manul-working.svg'), workingLight, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-working_dark.svg'), workingDark, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-working@20x20.svg'), workingLight, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-working@20x20_dark.svg'), workingDark, 'utf8');

// 3. Write manul-help from manul-help-v3 (fully animated CSS SVG)
const helpLight = applyPaletteColors(helpV3Raw, LIGHT_COLOR);
const helpDark = applyPaletteColors(helpV3Raw, DARK_COLOR);

fs.writeFileSync(path.join(ICONS_DIR, 'manul-help.svg'), helpLight, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-help_dark.svg'), helpDark, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-help@20x20.svg'), helpLight, 'utf8');
fs.writeFileSync(path.join(ICONS_DIR, 'manul-help@20x20_dark.svg'), helpDark, 'utf8');

// 4. Generate 8 static frames for manul_work_00..07 (for IntelliJ Swing runtime animation)
const EYE_OFFSETS = [0, 0, 1, 1, 0, 0, -1, -1];
const DOT_CONFIGS = [
  { d1: { y: 1, op: 1.0 }, d2: { y: 2, op: 0.35 }, d3: { y: 2, op: 0.35 } },
  { d1: { y: 2, op: 0.35 }, d2: { y: 1, op: 1.0 }, d3: { y: 2, op: 0.35 } },
  { d1: { y: 2, op: 0.35 }, d2: { y: 2, op: 0.35 }, d3: { y: 1, op: 1.0 } },
  { d1: { y: 2, op: 0.35 }, d2: { y: 2, op: 0.35 }, d3: { y: 2, op: 0.35 } },
  { d1: { y: 1, op: 1.0 }, d2: { y: 2, op: 0.35 }, d3: { y: 2, op: 0.35 } },
  { d1: { y: 2, op: 0.35 }, d2: { y: 1, op: 1.0 }, d3: { y: 2, op: 0.35 } },
  { d1: { y: 2, op: 0.35 }, d2: { y: 2, op: 0.35 }, d3: { y: 1, op: 1.0 } },
  { d1: { y: 2, op: 0.35 }, d2: { y: 2, op: 0.35 }, d3: { y: 2, op: 0.35 } },
];

function buildWorkFrame(frameIndex, color) {
  const eyeOffset = EYE_OFFSETS[frameIndex];
  const isLeftDown = (frameIndex % 2 === 0);
  const leftPawY = isLeftDown ? '23' : '22.2';
  const rightPawY = isLeftDown ? '22.2' : '23';
  const dots = DOT_CONFIGS[frameIndex];

  return `<svg xmlns="http://www.w3.org/2000/svg" class="mn mn4 mn4-work" color="${color}" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" role="img" aria-label="Manul pracuje (klatka ${frameIndex})">
<path stroke="${color}" fill="none" d="M3.5 10C3 7.5 4.5 5.5 6.5 5.5C8 5.5 9 6.5 9.5 8Q12 7.4 14.5 8C15 6.5 16 5.5 17.5 5.5C19.5 5.5 21 7.5 20.5 10L22 12.5L20.8 14L21.8 16.5L19.5 17.2Q12 21.5 4.5 17.2L2.2 16.5L3.2 14L2 12.5Z"/>
<path stroke="${color}" stroke-width="1.5" fill="none" d="M5.4 14.4L6.6 16.4M18.6 14.4L17.4 16.4"/>
<g fill="${color}" stroke="none">
<circle cx="${8 + eyeOffset}" cy="11.5" r="1.4" fill="${color}"/>
<circle cx="${16 + eyeOffset}" cy="11.5" r="1.4" fill="${color}"/>
</g>
<path stroke="${color}" fill="${color}" stroke-width="1.2" d="M11 14h2l-1 1z"/>
<path stroke="${color}" stroke-width="1.4" fill="none" d="M12 15.4Q11.2 16.6 10 16M12 15.4Q12.8 16.6 14 16"/>
<path stroke="${color}" fill="none" d="M6.4 ${leftPawY}q1.6-1.6 3.2 0"/>
<path stroke="${color}" fill="none" d="M14.4 ${rightPawY}q1.6-1.6 3.2 0"/>
<g fill="${color}" stroke="none">
<circle cx="9" cy="${dots.d1.y}" r="1.1" opacity="${dots.d1.op}" fill="${color}"/>
<circle cx="12" cy="${dots.d2.y}" r="1.1" opacity="${dots.d2.op}" fill="${color}"/>
<circle cx="15" cy="${dots.d3.y}" r="1.1" opacity="${dots.d3.op}" fill="${color}"/>
</g>
</svg>
`;
}

for (let i = 0; i < 8; i++) {
  const pad = String(i).padStart(2, '0');
  const light = buildWorkFrame(i, LIGHT_COLOR);
  const dark = buildWorkFrame(i, DARK_COLOR);

  fs.writeFileSync(path.join(ICONS_DIR, `manul_work_${pad}.svg`), light, 'utf8');
  fs.writeFileSync(path.join(ICONS_DIR, `manul_work_${pad}_dark.svg`), dark, 'utf8');
  fs.writeFileSync(path.join(ICONS_DIR, `manul_work_${pad}@20x20.svg`), light, 'utf8');
  fs.writeFileSync(path.join(ICONS_DIR, `manul_work_${pad}@20x20_dark.svg`), dark, 'utf8');
}

// 5. Generate 8 static frames for manul_help_00..07 (for IntelliJ Swing runtime animation)
const SHAKE_DX = [0, 0.7, 1.0, 0.7, 0, -0.7, -1.0, -0.7];

function buildHelpFrame(frameIndex, color) {
  const dx = SHAKE_DX[frameIndex];
  const showBang = frameIndex < 6;

  return `<svg xmlns="http://www.w3.org/2000/svg" class="mn mn4 mn4-help" color="${color}" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" role="img" aria-label="Manul potrzebuje pomocy (klatka ${frameIndex})">
<g transform="translate(${dx}, 0)">
<path stroke="${color}" fill="none" d="M3.5 10C3 7.5 4.5 5.5 6.5 5.5C8 5.5 9 6.5 9.5 8Q12 7.4 14.5 8C15 6.5 16 5.5 17.5 5.5C19.5 5.5 21 7.5 20.5 10L22 12.5L20.8 14L21.8 16.5L19.5 17.2Q12 21.5 4.5 17.2L2.2 16.5L3.2 14L2 12.5Z"/>
<path stroke="${color}" stroke-width="1.5" fill="none" d="M5.4 14.4L6.6 16.4M18.6 14.4L17.4 16.4"/>
<circle cx="8" cy="11.5" r="1.8" fill="${color}" stroke="none"/>
<circle cx="16" cy="11.5" r="1.8" fill="${color}" stroke="none"/>
<path stroke="${color}" fill="${color}" stroke-width="1.2" d="M11 14h2l-1 1z"/>
<path stroke="${color}" stroke-width="1.4" fill="none" d="M12 15.4Q11.2 16.6 10 16M12 15.4Q12.8 16.6 14 16"/>
</g>
${showBang ? `<g class="bang">
<path stroke="${color}" fill="none" d="M12 .9V2.4"/>
<circle cx="12" cy="4.4" r=".9" fill="${color}" stroke="none"/>
</g>` : ''}
</svg>
`;
}

for (let i = 0; i < 8; i++) {
  const pad = String(i).padStart(2, '0');
  const light = buildHelpFrame(i, LIGHT_COLOR);
  const dark = buildHelpFrame(i, DARK_COLOR);

  fs.writeFileSync(path.join(ICONS_DIR, `manul_help_${pad}.svg`), light, 'utf8');
  fs.writeFileSync(path.join(ICONS_DIR, `manul_help_${pad}_dark.svg`), dark, 'utf8');
  fs.writeFileSync(path.join(ICONS_DIR, `manul_help_${pad}@20x20.svg`), light, 'utf8');
  fs.writeFileSync(path.join(ICONS_DIR, `manul_help_${pad}@20x20_dark.svg`), dark, 'utf8');
}

console.log('Successfully generated all v3 Manul SVG icons with explicit palette colors!');
