# Report Structure Reference

The HTML report matches the structure of `~/.claude/usage-data/report.html`.
Use the same CSS class names and section IDs.

## Sections

| Section ID | Nav label | Content |
|---|---|---|
| `section-work` | What You Work On | `.project-area` cards |
| `section-usage` | How You Use CC | `.narrative` + charts |
| `section-wins` | Impressive Things | `.big-win` cards |
| `section-friction` | Where Things Go Wrong | `.friction-category` cards |
| `section-features` | Features to Try | `.feature-card` cards |
| `section-patterns` | New Usage Patterns | `.pattern-card` cards |
| `section-horizon` | On the Horizon | `.horizon-card` cards |
| `section-feedback` | Team Feedback | `.feedback-card` cards |

## New CSS Classes

```css
.trend-badge { display:inline-block; font-size:11px; font-weight:600;
  padding:2px 8px; border-radius:4px; margin-left:8px; }
.trend-declining  { background:#dcfce7; color:#166534; }
.trend-persistent { background:#fef9c3; color:#854d0e; }
.trend-emerging   { background:#fee2e2; color:#991b1b; }
.trend-note { font-size:12px; color:#64748b; margin-top:4px; }

.config-pill { display:inline-block; font-size:11px; font-weight:600;
  padding:2px 8px; border-radius:4px; margin-left:8px; }
.config-actionable { background:#dbeafe; color:#1e40af; }
.config-in-place   { background:#f1f5f9; color:#64748b; }
.config-partial    { background:#fef3c7; color:#92400e; }
.feature-card.muted { opacity:0.55; }

.report-meta { font-size:12px; color:#64748b; background:#f8fafc;
  border:1px solid #e2e8f0; border-radius:6px; padding:8px 14px;
  margin-bottom:24px; display:flex; gap:16px; flex-wrap:wrap; }
.report-meta span::before { content:"• "; }
.report-meta span:first-child::before { content:""; }
```

## Metadata Bar

Render immediately after `.subtitle`:
```html
<div class="report-meta">
  <span>{date_range}</span>
  <span>{session_count} sessions in scope</span>
  <span>--since {value}</span>   <!-- omit if no --since used -->
  <span>checkpoint: {path}</span>
</div>
```

## Trend Badge (inside .friction-category)

Add after `.friction-title`:
```html
<span class="trend-badge trend-{declining|persistent|emerging}">
  {↓ Declining | → Persistent | ↑ Emerging}
</span>
<div class="trend-note">{e.g. "5 in January, 0 since February"}</div>
```
Omit both elements when fewer than 3 weeks of data.

## Config Pill (inside .feature-card)

Add after `.feature-title`:
```html
<span class="config-pill config-{actionable|in-place|partial}">
  {Actionable | Already in place | Partially addressed}
</span>
```
Add class `muted` to `.feature-card` when status is `in-place`.
