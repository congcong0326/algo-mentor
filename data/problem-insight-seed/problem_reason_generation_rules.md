# 题目推荐理由生成规则

## 输入

批处理输入是一个 JSON 数组，每一项来自 `problem_reason_tasks.json`，包含题目 slug、题号、标题、难度、标签、内容状态和 LeetCode URL。生成时只使用这些元信息，不需要修改输入文件。

## 输出格式

输出必须是 JSON 数组。每一项只能包含以下字段：

```json
{
  "slug": "two-sum",
  "reasonEN": "English recommendation reason.",
  "reasonZH": "中文推荐理由。"
}
```

- `slug` 必须原样来自输入。
- `reasonEN` 和 `reasonZH` 必须是非空字符串。
- 不要输出 Markdown 代码块、注释、解释文本或额外字段。

## 内容结构

1. 拒绝废话：不要说“这道题给你一个矩阵...让你求...”这种题目描述。
2. 三段式结构：指出【核心题型分类/难度定位】+【核心技术动作/考点/易错点】+【该题的刷题/教学价值】。
3. 语气：专业、精炼、技术导向。
4. 每个推荐的句子开头要多样化，不要全是 这是，这题 类似的语法。

【好的参考样本】
- longest-substring-without-repeating-characters：最经典的可变滑动窗口题，训练去重和窗口左边界更新。
- largest-rectangle-in-histogram：单调栈核心代表题，训练利用“单调递增栈”寻找左右两侧第一个更小元素的技巧，配合“哨兵节点”简化边界处理，是攻克单调栈思维瓶颈的里程碑题。
- course-schedule-ii：拓扑排序模版题，不仅考查有向无环图（DAG）的入度统计与 BFS 遍历，更能直接映射到实际工程中的依赖循环检测与编译顺序规划。
