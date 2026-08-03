import type { SupportedLocale } from '../i18n/locales';

export type LegalDocumentKind = 'privacy' | 'terms';

export interface LegalDocumentSection {
  title: string;
  paragraphs?: string[];
  items?: string[];
}

export interface LegalDocumentContent {
  title: string;
  description: string;
  effectiveDateLabel: string;
  effectiveDate: string;
  backLabel: string;
  relatedLabel: string;
  sections: LegalDocumentSection[];
}

export const legalDocuments: Record<SupportedLocale, Record<LegalDocumentKind, LegalDocumentContent>> = {
  'zh-CN': {
    privacy: {
      title: '隐私政策',
      description: '本政策说明 Leet Mentor 在提供算法学习、练习与 AI 辅助功能时如何处理你的信息。',
      effectiveDateLabel: '生效日期',
      effectiveDate: '2026 年 8 月 3 日',
      backLabel: '返回 Leet Mentor',
      relatedLabel: '查看服务条款',
      sections: [
        {
          title: '1. 我们处理的信息',
          items: [
            '账号与身份信息：邮箱、昵称，以及你授权第三方登录服务提供的账号标识、公开资料或头像。',
            '学习与交互内容：学习计划、练习进度、代码提交、题目笔记、AI 对话、代码 Review、复习记录、学习画像和反馈内容。',
            '技术与安全信息：Session、CSRF 安全信息、访问时间、设备或浏览器信息，以及为排障和安全审计产生的必要日志。',
            '本地偏好：语言、主题等设置可能保存在浏览器本地存储中。',
          ],
        },
        {
          title: '2. 信息的使用目的',
          paragraphs: [
            '我们仅在提供和改进产品功能所需的范围内使用这些信息，包括账号登录、学习进度管理、生成训练计划、AI 讲解与 Review、错题复习、用户支持、安全防护和故障排查。',
          ],
        },
        {
          title: '3. AI 服务处理',
          paragraphs: [
            '使用 AI 功能时，题目上下文、你的提问、代码、计划需求以及与当前任务相关的学习信息，可能会发送给项目配置的 AI 模型服务商处理。请不要提交与学习任务无关的密码、密钥、证件信息或其他敏感个人信息。',
          ],
        },
        {
          title: '4. 信息共享',
          paragraphs: [
            '我们不会出售你的个人信息。为提供服务，必要信息可能由第三方登录服务、AI 模型服务商以及基础设施服务商处理。我们也可能在法律要求、保护用户安全或维护产品合法权益所必需时披露相关信息。',
          ],
        },
        {
          title: '5. 保存与安全',
          paragraphs: [
            '我们会在实现上述目的所需的期限内保存信息，并采取访问控制、会话保护、日志脱敏和最小权限等合理措施。没有任何系统能够保证绝对安全，请妥善保护账号凭据，并及时报告异常情况。',
          ],
        },
        {
          title: '6. 你的选择与权利',
          paragraphs: [
            '你可以在产品中查看或修改部分账号、偏好和学习信息，也可以通过产品内反馈入口或项目部署方提供的联系渠道提出访问、更正或删除请求。为满足安全、审计、备份或法律要求，部分记录可能需要在合理期限内继续保留。',
          ],
        },
        {
          title: '7. 未成年人',
          paragraphs: [
            '本服务不面向未满 14 周岁的儿童单独使用。未成年人应在监护人了解并同意后使用本服务，且不应提交不必要的个人信息。',
          ],
        },
        {
          title: '8. 政策更新与联系',
          paragraphs: [
            '我们可能根据功能、处理方式或法律要求更新本政策，并在页面中更新生效日期。内测期间如有隐私问题，请通过产品内反馈入口或项目部署方提供的联系渠道联系我们。',
          ],
        },
      ],
    },
    terms: {
      title: '服务条款',
      description: '本条款适用于你对 Leet Mentor 算法学习与 AI 辅助服务的访问和使用。',
      effectiveDateLabel: '生效日期',
      effectiveDate: '2026 年 8 月 3 日',
      backLabel: '返回 Leet Mentor',
      relatedLabel: '查看隐私政策',
      sections: [
        {
          title: '1. 条款接受',
          paragraphs: [
            '访问、注册或继续使用本服务，即表示你已阅读并同意本条款和隐私政策。如果你不同意，请停止使用相关服务。',
          ],
        },
        {
          title: '2. 服务内容',
          paragraphs: [
            'Leet Mentor 提供算法学习计划、题库练习、学习进度管理、AI 讲解、代码 Review、复习和学习画像等功能。内测期间，功能、可用范围和运行方式可能持续调整。',
          ],
        },
        {
          title: '3. 账号责任',
          paragraphs: [
            '你应提供合法、准确的账号信息，妥善保管登录凭据，并对账号下的操作负责。发现未经授权的访问时，应尽快通过产品内反馈入口或部署方渠道报告。',
          ],
        },
        {
          title: '4. 合理使用',
          items: [
            '不得利用本服务从事违法、侵权、欺诈或危害他人的活动。',
            '不得绕过权限、探测漏洞、干扰服务运行，或以自动化方式造成不合理负载。',
            '不得上传无权处理的内容，或在学习任务中提交密码、密钥等不必要的敏感信息。',
          ],
        },
        {
          title: '5. AI 内容说明',
          paragraphs: [
            'AI 生成的计划、讲解、建议和 Review 可能不准确、不完整或不适合你的具体情况。你应独立核验重要结论、代码正确性和复杂度分析。本服务不保证学习、考试或求职结果。',
          ],
        },
        {
          title: '6. 你的内容',
          paragraphs: [
            '你应确保有权提交代码、笔记、对话和反馈等内容。你保留对这些内容依法享有的权利，同时允许我们在提供、维护和改进本服务所必需的范围内存储、处理和传输这些内容。',
          ],
        },
        {
          title: '7. 服务调整与账号处理',
          paragraphs: [
            '我们可能因内测安排、维护、安全风险或功能调整暂停或变更部分服务。对于违反本条款、危害系统安全或他人权益的账号，我们可以限制访问或终止服务。',
          ],
        },
        {
          title: '8. 责任边界与条款更新',
          paragraphs: [
            '在法律允许的范围内，本服务按现状提供，不保证持续、无错误或完全满足特定目的。我们可能更新本条款并在页面中调整生效日期。内测期间如有问题，请通过产品内反馈入口或项目部署方提供的联系渠道联系我们。',
          ],
        },
      ],
    },
  },
  'en-US': {
    privacy: {
      title: 'Privacy Policy',
      description: 'This policy explains how Leet Mentor handles information while providing algorithm learning, practice, and AI-assisted features.',
      effectiveDateLabel: 'Effective date',
      effectiveDate: 'August 3, 2026',
      backLabel: 'Back to Leet Mentor',
      relatedLabel: 'View Terms of Service',
      sections: [
        {
          title: '1. Information we process',
          items: [
            'Account and identity information, including your email, display name, and profile details authorized through a third-party sign-in provider.',
            'Learning and interaction content, including plans, progress, code submissions, notes, AI conversations, code reviews, review records, learner profiles, and feedback.',
            'Technical and security information, including sessions, CSRF security data, access times, device or browser information, and logs needed for diagnostics and security audits.',
            'Local preferences, such as language and theme, which may be stored in your browser.',
          ],
        },
        {
          title: '2. How we use information',
          paragraphs: [
            'We use information only as needed to provide and improve account access, learning progress, training plans, AI explanations and reviews, spaced review, support, security, and diagnostics.',
          ],
        },
        {
          title: '3. AI processing',
          paragraphs: [
            'When you use AI features, problem context, prompts, code, plan requirements, and learning information relevant to the task may be sent to the configured AI model provider. Do not submit passwords, secrets, identity documents, or unrelated sensitive personal information.',
          ],
        },
        {
          title: '4. Sharing',
          paragraphs: [
            'We do not sell personal information. Information may be processed by sign-in providers, AI model providers, and infrastructure providers as needed to deliver the service. We may also disclose information when legally required or necessary to protect users and the service.',
          ],
        },
        {
          title: '5. Retention and security',
          paragraphs: [
            'We retain information for as long as needed for the purposes above and use reasonable measures such as access controls, session protection, log redaction, and least-privilege access. No system can guarantee absolute security, so keep your credentials secure and report suspicious activity promptly.',
          ],
        },
        {
          title: '6. Your choices and rights',
          paragraphs: [
            'You can view or update certain account, preference, and learning information in the product. You may request access, correction, or deletion through the in-product feedback channel or a contact channel provided by the operator of your deployment. Some records may be retained for security, audit, backup, or legal obligations.',
          ],
        },
        {
          title: '7. Children',
          paragraphs: [
            'The service is not intended for independent use by children under 14. Minors should use it only with a guardian\'s knowledge and consent and should not submit unnecessary personal information.',
          ],
        },
        {
          title: '8. Updates and contact',
          paragraphs: [
            'We may update this policy as features, processing practices, or legal requirements change and will revise the effective date on this page. During beta, contact us through in-product feedback or a channel provided by the operator of your deployment.',
          ],
        },
      ],
    },
    terms: {
      title: 'Terms of Service',
      description: 'These terms govern your access to and use of the Leet Mentor algorithm learning and AI-assisted service.',
      effectiveDateLabel: 'Effective date',
      effectiveDate: 'August 3, 2026',
      backLabel: 'Back to Leet Mentor',
      relatedLabel: 'View Privacy Policy',
      sections: [
        {
          title: '1. Acceptance',
          paragraphs: [
            'By accessing, registering for, or continuing to use the service, you acknowledge these terms and the Privacy Policy. If you do not agree, stop using the service.',
          ],
        },
        {
          title: '2. Service scope',
          paragraphs: [
            'Leet Mentor provides learning plans, problem practice, progress tracking, AI explanations, code reviews, spaced review, and learner profile features. During beta, features, availability, and operation may change.',
          ],
        },
        {
          title: '3. Account responsibility',
          paragraphs: [
            'Provide lawful and accurate account information, protect your credentials, and take responsibility for activity under your account. Report unauthorized access through in-product feedback or a deployment contact channel as soon as possible.',
          ],
        },
        {
          title: '4. Acceptable use',
          items: [
            'Do not use the service for unlawful, infringing, fraudulent, or harmful activity.',
            'Do not bypass permissions, probe vulnerabilities, disrupt operation, or create unreasonable automated load.',
            'Do not submit content you have no right to process or include unnecessary passwords, secrets, or sensitive information in learning tasks.',
          ],
        },
        {
          title: '5. AI content',
          paragraphs: [
            'AI-generated plans, explanations, suggestions, and reviews may be inaccurate, incomplete, or unsuitable for your circumstances. Independently verify important conclusions, code correctness, and complexity analysis. The service does not guarantee learning, examination, or employment outcomes.',
          ],
        },
        {
          title: '6. Your content',
          paragraphs: [
            'You must have the right to submit code, notes, conversations, and feedback. You retain any rights you lawfully hold and allow us to store, process, and transmit the content only as needed to provide, maintain, and improve the service.',
          ],
        },
        {
          title: '7. Changes and account action',
          paragraphs: [
            'We may suspend or change parts of the service for beta operations, maintenance, security, or feature updates. We may restrict or terminate access for violations, security threats, or harm to others.',
          ],
        },
        {
          title: '8. Service limitations and updates',
          paragraphs: [
            'To the extent permitted by law, the service is provided as available without a guarantee of uninterrupted or error-free operation or fitness for a particular purpose. We may update these terms and revise the effective date on this page. During beta, contact us through in-product feedback or a channel provided by the operator of your deployment.',
          ],
        },
      ],
    },
  },
};
