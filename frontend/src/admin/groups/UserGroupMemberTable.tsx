import { Trash2 } from 'lucide-react';
import { useI18n } from '../../i18n/I18nProvider';
import type { UserGroupMember } from '../../types/api';
import { formatDateTime } from '../ai/aiFormat';

interface UserGroupMemberTableProps {
  loading: boolean;
  members: UserGroupMember[];
  onOpenUser: (userId: number) => void;
  onRemove: (member: UserGroupMember) => void;
}

export default function UserGroupMemberTable({ loading, members, onOpenUser, onRemove }: UserGroupMemberTableProps) {
  const { resources } = useI18n();
  const t = resources.adminGroups;
  return (
    <div className="admin-table-wrap">
      <table className="admin-data-table user-group-members-table">
        <thead><tr><th>{t.memberUser}</th><th>{t.email}</th><th>{t.accountStatus}</th><th>{t.joinedAt}</th><th>{t.expiresAt}</th><th>{t.actions}</th></tr></thead>
        <tbody>
          {loading ? <tr><td colSpan={6}>{t.membersLoading}</td></tr> : null}
          {!loading && members.map((member) => (
            <tr key={member.userId}>
              <td><button className="admin-table-link inline" onClick={() => onOpenUser(member.userId)} type="button"><strong>{member.displayName || resources.app.unknownUser(member.userId)}</strong><small>#{member.userId}</small></button></td>
              <td>{member.email || resources.common.empty}</td>
              <td><span className={`admin-status-badge ${member.status.toLowerCase()}`}>{t.userStatuses[member.status]}</span></td>
              <td>{formatDateTime(member.joinedAt)}</td>
              <td>{member.expiresAt ? formatDateTime(member.expiresAt) : t.neverExpires}</td>
              <td><button aria-label={t.removeMember(member.displayName || `#${member.userId}`)} className="icon-button compact danger-icon-button" onClick={() => onRemove(member)} title={t.remove} type="button"><Trash2 aria-hidden="true" /></button></td>
            </tr>
          ))}
          {!loading && members.length === 0 ? <tr><td colSpan={6}>{t.membersEmpty}</td></tr> : null}
        </tbody>
      </table>
    </div>
  );
}
