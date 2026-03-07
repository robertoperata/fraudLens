function colorClass(score: number): string {
  if (score >= 70) return 'bg-red-100 text-red-800';
  if (score >= 40) return 'bg-yellow-100 text-yellow-800';
  return 'bg-green-100 text-green-800';
}

export function RiskScoreBadge({ score }: { score: number }) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colorClass(score)}`}>
      {score}/100
    </span>
  );
}
