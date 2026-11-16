/** Side of the cropped picture in pixels; the backend scales every avatar to this size anyway. */
export const CROP_SIZE = 512;
export const MAX_ZOOM = 3;

/** Where the member placed the picture: the zoom (1 = the largest square) and the kept square's centre in image pixels. */
export interface Crop {
  zoom: number;
  centerX: number;
  centerY: number;
}

/** A centred crop at zoom 1: the round avatar shows the middle of the picture. */
export const centeredCrop = (width: number, height: number): Crop => ({
  zoom: 1,
  centerX: width / 2,
  centerY: height / 2,
});

/** The square of the image that is kept, moved back inside the image when the centre is too close to an edge. */
export function cropSquare(
  width: number,
  height: number,
  crop: Crop,
): { x: number; y: number; side: number } {
  const side = Math.min(width, height) / crop.zoom;
  const start = (center: number, full: number) =>
    Math.min(Math.max(center - side / 2, 0), full - side);
  return { x: start(crop.centerX, width), y: start(crop.centerY, height), side };
}

/**
 * The crop moved by (dx, dy) image pixels and/or zoomed, kept inside the image.
 * Storing the clamped centre means a drag past an edge does not have to be dragged back first.
 */
export function moveCrop(
  width: number,
  height: number,
  crop: Crop,
  dx: number,
  dy: number,
  zoom = crop.zoom,
): Crop {
  const next = { zoom, centerX: crop.centerX + dx, centerY: crop.centerY + dy };
  const { x, y, side } = cropSquare(width, height, next);
  return { zoom, centerX: x + side / 2, centerY: y + side / 2 };
}
