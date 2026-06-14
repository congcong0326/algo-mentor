import type { ApiResponse, HealthStatus } from '../types/api';

const jsonHeaders = {
  Accept: 'application/json',
};

export async function getHealth(): Promise<ApiResponse<HealthStatus>> {
  const response = await fetch('/api/health', {
    headers: jsonHeaders,
  });

  if (!response.ok) {
    throw new Error(`Health request failed with status ${response.status}`);
  }

  return response.json();
}

