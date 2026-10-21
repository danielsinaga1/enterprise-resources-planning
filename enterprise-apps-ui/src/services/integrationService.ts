import api from './api';
import type { ApiResponse } from '../types';

// ============================================================
// INTEGRATION SERVICES
// Thin wrappers over the proxy endpoints that call external APIs
// (PubChem for LIMS chemicals, Open-Meteo for IRMS incident weather)
// ============================================================

// ──────────────────────────────────────────────────────────────
// PubChem — chemical compound enrichment
// ──────────────────────────────────────────────────────────────

export interface PubChemData {
  cid: number;
  iupacName?: string;
  molecularFormula?: string;
  molecularWeight?: number;
  inchiKey?: string;
  smiles?: string;
  ghsPictograms?: string[];
  ghsSignalWord?: string;
  hazardStatements?: string[];
}

export const pubchemService = {
  /**
   * Fetches PubChem enrichment for a LIMS compound.
   * Returns null when PubChem has no match (backend returns 204).
   */
  async enrich(compoundId: number): Promise<PubChemData | null> {
    try {
      const resp = await api.get<ApiResponse<PubChemData>>(
        `/api/v1/master/lims-compounds/${compoundId}/pubchem`
      );
      if (resp.status === 204) return null;
      return (resp.data as ApiResponse<PubChemData>).data as PubChemData;
    } catch {
      return null;
    }
  },
};

// ──────────────────────────────────────────────────────────────
// Open-Meteo — historical weather for incidents
// ──────────────────────────────────────────────────────────────

export interface WeatherData {
  description: string;
  temperatureCelsius?: number;
  windSpeedKmh?: number;
  precipitationMm?: number;
  weatherCode?: number;
  facilityLatitude: number;
  facilityLongitude: number;
  date: string;
  hour: number;
}

export const weatherService = {
  /**
   * Fetches historical weather for an incident.
   * Returns null when the incident is too recent (<5 days) or the API is unreachable (204).
   */
  async forIncident(incidentId: number): Promise<WeatherData | null> {
    try {
      const resp = await api.get<ApiResponse<WeatherData>>(
        `/api/v1/incident-reports/${incidentId}/weather`
      );
      if (resp.status === 204) return null;
      return (resp.data as ApiResponse<WeatherData>).data as WeatherData;
    } catch {
      return null;
    }
  },
};
