---
name: generate:diagram
description: Generate a technical diagram with mermaid.js 
user_invocable: true
arguments:
  - name: type
    description: The type of diagram to generate
    required: true
  - name: prompt
    description: The user's instructions detailing what to diagram.
    required: true
---
# Generate Diagram

Create the mermaid.js diagram requested by the user.

## Steps

1. Read the `$ARGUMENTS.prompt` to understand what the user wants to represent.
2. Plan the diagram representation, following the rules for the type of diagram given by `$ARGUMENTS.type`.


## Planning What to Diagram
We want to diagram all the things we need to visualize to understand the process, information flow, or system we care about. We want to visualize major steps and data involved in system processes. Be pragmatic: good diagramming is not rigidly about classes or services, or a particular "type" of information. We prefer to use modified data flow diagrams with the conventions defined below.

## Types of Diagrams

### Data Flow Diagrams
[Data Flow Diagrams](https://en.wikipedia.org/wiki/Data-flow_diagram) (DFDs) convey the origin, system of record, and conveyors of data. We use a modified version of DFDs for system design. 

Our diagrams adopt the following conventions:

- Every diagram should have a title describing what it is about
- Most diagrams should require neither a legend, nor much use of color.
- Legends are often an indication of inconsistency or complexity, although they may sometimes be necessary.
- A process is anything that generates or transforms information or does interesting work.
- We represent processes as rounded rectangles with the process/function/service name in bold.
- If the transformation isn’t evident from the name/input/output, it should be described by a label.
- Typical information stores include databases, queues, and file systems.
- We represent databases with cylinders.
- The flow of data is of primary interest in data flow diagrams.
- We depict the direction of information travel with an arrow pointing to the receiver/target of the transfer.
- All I/O communication (e.g. read/fetch, write/send) should be depicted and labeled to describe the information that is traveling unless it is obvious from either the origin or destination.
- We use labels sparingly to attach extra information to other diagram elements.
- We represent labels as rectangles with embedded text.
- Labels are attached to the items they describe by a solid line with no adornments or arrows.
- The [Flowchart](https://mermaid.js.org/syntax/flowchart.html) diagram type should be used for Data Flow Diagrams

#### Best Practices
- a diagram should contain a minimum of 3 processes to convey something interesting
- a diagram should contain a maximum of 9 processes to maintain focus

### Entity Relationship Diagrams
- Entity Relationship (ER) diagrams depict the entities in the domain and include their relationships and the cardinality of those relationships. They are pre-requisite for  database schema creation (the physical data model). We use the Crow's Foot notation when creating ER diagrams.
- See the [Wikipedia page on ER diagrams](https://en.wikipedia.org/wiki/Entity%E2%80%93relationship_model) 
- The [ER](https://mermaid.js.org/syntax/entityRelationshipDiagram.html) diagram type should be used for ER Diagrams

## State Diagrams
- State Diagrams are good for modeling processes and Finite State Machines. 
- The [State Diagram](https://mermaid.js.org/syntax/stateDiagram.html) type should be used for State Diagrams.

### Sequence Diagrams
- [Sequence Diagrams](https://en.wikipedia.org/wiki/Sequence_diagram) show process interactions arranged in time sequence. Tip: prefer labeling lines with wording that conveys ordering vs adding sequence numbers prefer numbering processes over lines.
- The [Sequence](https://mermaid.js.org/syntax/sequenceDiagram.html) diagram type should be used for Sequence Diagrams.

### Component Diagrams
- [Component Diagrams](https://en.wikipedia.org/wiki/Component_diagram) depict the runtime structure of a system. Components are abstract concepts that can represent parts of a system at different levels of scale. [C4](https://c4model.com/) diagrams are component diagrams.
- The [C4](https://mermaid.js.org/syntax/c4.html) diagram type should be used for Component Diagrams.

## Rendering to Image Files with mmdc

The `mmdc` CLI tool (mermaid-cli) renders `.mmd` or `.md` source files into image files. Use it when the user wants a PNG, SVG, or PDF output rather than inline Markdown.

### Basic Usage

Write the diagram source to a `.mmd` file, then render it:

```bash
# SVG (default when no -e flag)
mmdc -i diagram.mmd -o diagram.svg

# PNG
mmdc -i diagram.mmd -o diagram.png

# PDF
mmdc -i diagram.mmd -o diagram.pdf
```

The output format is inferred from the `-o` file extension. Override it explicitly with `-e svg|png|pdf`.

### Common Options

| Flag | Description | Example |
|------|-------------|---------|
| `-i` | Input `.mmd` or `.md` file (required) | `-i input.mmd` |
| `-o` | Output file path | `-o output.png` |
| `-t` | Theme: `default`, `forest`, `dark`, `neutral` | `-t dark` |
| `-b` | Background color for PNG/SVG | `-b transparent` or `-b '#F0F0F0'` |
| `-w` | Page width in pixels (default 800) | `-w 1200` |
| `-H` | Page height in pixels (default 600) | `-H 900` |
| `-s` | Puppeteer scale factor (default 1) | `-s 2` |
| `-c` | JSON config file for mermaid options | `-c config.json` |
| `-C` | CSS file for custom styling/animation | `-C style.css` |
| `-q` | Suppress log output | `-q` |
| `-e` | Force output format | `-e png` |

### Practical Examples

**PNG with dark theme and transparent background:**
```bash
mmdc -i input.mmd -o output.png -t dark -b transparent
```

**High-resolution PNG (2× scale):**
```bash
mmdc -i diagram.mmd -o diagram.png -s 2 -w 1600 -H 1200
```

**SVG with custom CSS animations:**
```bash
mmdc -i diagram.mmd -o diagram.svg --cssFile style.css
```

**Process all diagrams in a Markdown file** (generates separate SVG artefacts and updates image references in the output):
```bash
mmdc -i readme.template.md -o readme.md
```

**Read diagram from stdin, write SVG to stdout:**
```bash
echo "graph LR; A-->B" | mmdc -i - -o - -e svg
```

### Workflow

1. Write mermaid source to a `.mmd` file (or inline in `.md`).
2. Run `mmdc` with the desired output format and options.
3. If the user needs a specific size or theme, apply `-w`, `-H`, `-s`, and `-t` flags.
4. For transparent backgrounds (common in dark-mode contexts), use `-b transparent`.


