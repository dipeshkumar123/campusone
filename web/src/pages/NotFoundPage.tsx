import { Link } from 'react-router-dom';
import { ArrowLeft, Compass, LockKeyhole } from 'lucide-react';

export function NotFoundPage({ forbidden = false }: { forbidden?: boolean }) {
  return (
    <div className="not-found">
      <span className="not-found-icon">{forbidden ? <LockKeyhole size={26} /> : <Compass size={26} />}</span>
      <p className="eyebrow">{forbidden ? 'Not in your workspace' : '404'}</p>
      <h1>{forbidden ? 'This screen is outside your role.' : 'That page is not on the map.'}</h1>
      <p className="muted">
        {forbidden
          ? 'Your demo account can only open the workspaces assigned to its role. Switch accounts to explore the other side of CampusOne.'
          : 'The page may have moved, or the link may be incomplete.'}
      </p>
      <Link to="/" className="btn btn-primary">
        <ArrowLeft size={16} /> Back to overview
      </Link>
    </div>
  );
}
