/**
 * Icon mapping for asset type display.
 * Resolution priority: 1) tipoBien classification (vehicle), 2) keyword match in name, 3) default icon.
 */
import {
  SportCarRacing,
  DevicesMacbook,
  MediaMonitor,
  DevicesSmartphone,
  DevicesTvBox,
  FilesPrint,
  GenericPicture,
  SoftwareCode,
  TravelBed,
  ShopBag,
  TravelHotel,
  DevicesKeyboard,
  DevicesMouse,
  GenericHome,
} from "@heathmont/moon-icons";

/** Fallback icon used when no keyword matches the asset name. */
export const DEFAULT_TIPO_ACTIVO_ICON = ShopBag;

/** Keyword-to-icon mappings evaluated in order until a match is found. */
const MAPEOS = [
  { keywords: ["auto", "carro", "vehículo", "vehiculo", "coche", "camioneta", "moto", "motocicleta"], icon: SportCarRacing },
  { keywords: ["laptop", "portátil", "portatil", "computadora", "pc ", "notebook", "macbook"], icon: DevicesMacbook },
  { keywords: ["monitor", "pantalla", "display", "proyector"], icon: MediaMonitor },
  { keywords: ["teléfono", "telefono", "celular", "phone", "smartphone"], icon: DevicesSmartphone },
  { keywords: ["impresora", "printer", "multifuncional"], icon: FilesPrint },
  { keywords: ["cámara", "camara", "camera", "videocámara"], icon: GenericPicture },
  { keywords: ["televisión", "television", "tv ", "televisor"], icon: DevicesTvBox },
  { keywords: ["mesa", "escritorio", "silla", "mobiliario", "mueble"], icon: TravelBed },
  { keywords: ["teclado", "keyboard"], icon: DevicesKeyboard },
  { keywords: ["mouse", "ratón", "raton"], icon: DevicesMouse },
  { keywords: ["caja", "almacén", "almacen", "inventario", "estantería"], icon: ShopBag },
  { keywords: ["aire", "acondicionado", "ventilador", "clima"], icon: GenericHome },
];

/**
 * Returns the display icon component for a given asset type.
 * @param {{ nombre?: string, tipoBien?: string }} item - Asset type object
 * @returns {import("react").ComponentType} Icon component to render
 */
export function getTipoActivoIcon(item) {
  const n = (item?.nombre ?? "").toLowerCase().trim();
  const tipoBien = (item?.tipoBien ?? "").toLowerCase();

  // 1) Vehicle classification based on tipoBien (Inmueble maps to vehicle in this UI context)
  if (tipoBien.includes("inmueble")) return SportCarRacing;

  // 2) Keyword match against the asset name
  for (const { keywords, icon } of MAPEOS) {
    if (keywords.some((k) => n.includes(k))) return icon;
  }

  // 3) No match found — return the default generic icon
  return DEFAULT_TIPO_ACTIVO_ICON;
}
