---
name: decision-matrix
description: Construct a decision matrix to compare candidate solutions against weighted criteria. Use whenever the user wants to compare options, evaluate alternatives, weigh tradeoffs, choose between approaches, rank candidates, or make a structured decision — even if they don't say "decision matrix" explicitly. Phrases like "should I A or B?", "help me decide between X and Y", "what are the tradeoffs?", "compare these options", "pros and cons of...", "which is better?", or any deliberation across multiple options should trigger this skill.
user_invocable: true
---

# Decision Matrix

A decision matrix is a structured comparison of candidate solutions against
evaluation criteria. It exposes tradeoffs that prose discussion tends to bury,
and forces the reasoner to confront cases where their preferred option ranks
poorly. Use it whenever a real choice exists.

## When to use a matrix

Reach for the matrix when:

- there are 2+ candidate solutions to compare
- the criteria are not all equally important
- evidence about each option is uneven (you know more about some than others)
- the discussion keeps circling without converging
- a defensible recommendation is needed, not just a personal preference

If only one candidate is on the table, the matrix is not yet useful —
generate at least one alternative first. At minimum, include the status quo
("do nothing") as a candidate. If categorical alternatives the user has not
mentioned exist, ask whether they were considered before populating the
matrix.

## Structure

The matrix is a table laid out as follows:

- **Top-left cell**: the problem statement (in a spreadsheet this is `A1`)
- **Column headers**: short names for each candidate solution
- **Row headers**: evaluation criteria, in order of decreasing importance
- **Intersecting cells**: a description of how the candidate performs against the criterion

Criteria are weighted by **order**, not by number. The first row is the most
important criterion; rows below it matter less. This avoids spurious
precision (numeric weights like 0.35 and 0.30 imply more discrimination than
the reasoner usually has) while preserving real priority information.

## Cell content

Each intersecting cell holds **descriptive text**, not a score. Scores
collapse context and nuance into a number that pretends to be objective.
Descriptive text preserves the reasoning that supports the judgment, so a
reader can disagree productively rather than haggling over numbers.

Keep cells terse — a short fragment or sentence each. The whole matrix
should be scannable in one read.

## Verdict signals

Each cell carries an optional color signal expressing how the candidate
fares on that criterion. In a markdown table, prefix the cell text with one
of the following emoji:

- 🟢 — clearly favorable on this criterion
- 🟡 — meaningful tradeoff, drawback, or partial fit
- 🔴 — disqualifying weakness; rules the candidate out

Cells that are neither favorable nor unfavorable receive **no** prefix.
Resist coloring every cell. Neutral judgments are common and honest; forcing
a verdict where none exists hides the very uncertainty the matrix should
surface.

A column with only 🟢 is suspicious. So is one with only 🔴. Almost every
real option has a mix of pros and cons; a single-color column usually
signals bias, incomplete criteria, or a candidate that hasn't been
scrutinized as carefully as the others. When you see this, pause and
challenge the analysis before drawing conclusions.

Use 🔴 sparingly. Reserve it for evaluations that genuinely disqualify a
solution, not for "I dislike this." Reaching for red on multiple candidates
across multiple criteria typically means the criteria are encoding
preferences as hard constraints — revisit them.

## Output format

Render the matrix as a markdown table. The problem statement occupies the
top-left header cell:

```
| Problem statement here              | Candidate A     | Candidate B      | Candidate C (status quo) |
| ----------------------------------- | --------------- | ---------------- | ------------------------ |
| Most important criterion            | 🟢 brief eval   | 🟡 brief eval    | 🔴 brief eval            |
| Next most important criterion       | brief eval      | 🟢 brief eval    | brief eval               |
| Less important criterion            | 🟡 brief eval   | brief eval       | 🟢 brief eval            |
```

If the status quo is one of the candidates, label it explicitly
(`Candidate X (status quo)`) so its role as the do-nothing baseline is
clear.

## After the matrix

Once the matrix is rendered, propose a provisional selection. The strongest
candidate is **not** automatically the one with the most 🟢 cells — a
candidate that does well on the top several criteria typically beats one
that only wins the first row, and a single 🔴 on a critical criterion
outweighs many 🟢 on minor ones. State the recommendation, name the
candidate it beats, and identify which criteria decided the call.
Acknowledge any 🔴 in the recommended candidate as a residual risk to
manage rather than glossing over it.

If a candidate cannot be picked with confidence, say so and name the
specific additional information that would break the tie. Indecision
surfaced is more useful than false certainty.

## Choosing criteria

Good evaluation criteria come from:

- the goal the problem statement implies — does the solution achieve it?
- attributes that distinguish candidates — a criterion that every option
  scores the same on does not help discriminate, so drop it
- things the user dislikes about the status quo — these tend to rank highly
  among priorities and are often the reason the problem is being solved at all

Cost and time are common criteria, but should not be assumed to be the most
important. List them when relevant, but place them by their actual priority
rather than by reflex.

If the user has not stated criteria, propose 3–6 candidate criteria and let
them refine the list before populating the matrix. Asking "what matters to
you here?" often surfaces priorities that were implicit and unspoken.
