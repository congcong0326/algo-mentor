# 学习计划模板源数据

本目录是学习计划模板的人工维护入口。`data/learning-plan-template-seed/` 是后端导入使用的聚合产物，不直接手工编辑。

## 目录规则

- `template_order.json`：声明需要进入聚合 seed 的模板顺序。
- `templates/<templateId>/template.json`：单个模板的主体信息和阶段规划。
- `templates/<templateId>/problem_refs.jsonl`：单个模板的题目引用明细。

`template.json` 不维护 `difficultyMix`、本地匹配数、缺失题列表和 `sourceTags` 这类派生统计；`problem_refs.jsonl` 不维护 `metadata.matchedLocalProblem`。这些字段由生成器根据当前 `data/seed/problems.jsonl` 重新计算。

## 生成命令

当前由批次生成器管理的 13 个模板包括 TIH、动态规划进阶、剑指 Offer、算法模式入门、程序员面试金典、LeetCode 75、LeetCode 面试经典 150、代码随想录完整版、labuladong 算法框架、SQL 50、JavaScript 30 天和两套 Pandas 计划，可通过固定来源版本重新生成：

```bash
python3 tools/learning_plan_template_seed/prepare_p1b_template_sources.py
```

其中代码随想录固定到 commit `86f78fde8cb62d10c3b5e38b7e6b6e0705850f92`，labuladong 固定到 commit `b1f23cb9605f6146ff78bafad71e795176439b99`，LeetCode 官方计划记录为 `accessed-2026-07-28`。当前聚合 seed 共 35 个模板 / 1738 refs / 1699 匹配 / 39 缺失。

生成或修改单模板源文件后，再统一重建聚合 seed：

```bash
python3 tools/learning_plan_template_seed/prepare_template_seed.py
```

生成结果固定写入 `data/learning-plan-template-seed/` 下的四个文件：

- `learning_plan_templates.jsonl`
- `learning_plan_template_problem_refs.jsonl`
- `learning_plan_template_seed_manifest.json`
- `learning_plan_template_seed_metadata.md`
