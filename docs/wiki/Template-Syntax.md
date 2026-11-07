# Template Syntax

## Variables

Write `{{name}}` anywhere in a text block. Add a filter after a pipe character.

| Filter | Example | Result |
|---|---|---|
| `money` | `{{price\|money}}` | Price in your currency format |
| `number` | `{{x\|number}}` | Number in your format |
| `upper` | `{{x\|upper}}` | Upper case |
| `lower` | `{{x\|lower}}` | Lower case |

## Built-in values

`{{date}}`, `{{time}}`, `{{datetime}}`, `{{business.name}}`, `{{business.address}}`, `{{business.phone}}`, `{{business.taxId}}`, `{{business.footer}}`. Labels also get `{{serial}}`.

## Show only if

Every block has a "Show only if" field. Examples:

```
discount > 0
customer
!paid
status == "paid" && total >= 100
```

## Tables

A table block reads rows from a list variable, usually `items`. Each row has the fields you name, such as `name`, `qty`, `price` and `total`.
