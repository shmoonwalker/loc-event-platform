import { centeredCrop, cropSquare, moveCrop } from './avatar-crop';

describe('avatar crop', () => {
  it('keeps the largest centred square of a portrait picture at zoom 1', () => {
    expect(cropSquare(400, 1000, centeredCrop(400, 1000))).toEqual({ x: 0, y: 300, side: 400 });
  });

  it('keeps the square inside the picture when moved past an edge', () => {
    const crop = moveCrop(400, 1000, centeredCrop(400, 1000), 0, 5000);
    expect(cropSquare(400, 1000, crop)).toEqual({ x: 0, y: 600, side: 400 });
  });

  it('moves the square back inside when zooming out near an edge', () => {
    const zoomed = moveCrop(1000, 1000, centeredCrop(1000, 1000), 5000, 0, 2);
    expect(cropSquare(1000, 1000, zoomed)).toEqual({ x: 500, y: 250, side: 500 });

    const out = moveCrop(1000, 1000, zoomed, 0, 0, 1);
    expect(cropSquare(1000, 1000, out)).toEqual({ x: 0, y: 0, side: 1000 });
  });
});
