---
slug: hashmap-collision
tags: [Java集合, 哈希表]
status: published
order: 20
relations:
  prerequisites: [java-pass-by-value]
  related: [java-pass-by-value]
---

HashMap 会把哈希定位到同一个桶的条目放在桶内，并结合键的相等性区分条目。

## 结构示意

桶内可以包含多个条目；满足条件时，链表可转换为树形结构。

## 演示说明

这里的关联仅用于演示框架：前置知识与一般关联可以分别展示，并按 slug 跳转。

### 详情可自由组织

三级标题、列表和其他 Markdown 内容都直接渲染，无需新增数据库字段。
