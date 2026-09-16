INSERT INTO knowledge_outline_node(parent_id,node_kind,slug,title,summary,sort_order,status)
SELECT NULL,'ROOT','root','知识库根节点','系统虚拟根节点',0,'PUBLISHED'
WHERE NOT EXISTS (SELECT 1 FROM knowledge_outline_node WHERE node_kind='ROOT');
INSERT INTO knowledge_outline_node(parent_id,node_kind,slug,title,summary,sort_order,status)
SELECT r.id,'NODE','test-java','测试 Java 大纲','测试用知识大纲，可用于接口联调',1,'PUBLISHED'
FROM knowledge_outline_node r WHERE r.node_kind='ROOT' AND NOT EXISTS (SELECT 1 FROM knowledge_outline_node WHERE slug='test-java');
INSERT INTO knowledge_card(outline_node_id,question,answer_markdown,explanation_markdown,sort_order,status)
SELECT n.id,'测试卡片：Java 对象与引用是什么？','测试答案：引用变量保存对象地址，对象存储在堆中。','这是用于验证知识库浏览、答案展示和复习评价的最小测试卡片。',1,'PUBLISHED'
FROM knowledge_outline_node n WHERE n.slug='test-java' AND NOT EXISTS (SELECT 1 FROM knowledge_card WHERE question LIKE '测试卡片%');
