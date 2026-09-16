package moe.https.syncthing.ui.resources

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp


val Syncthing: ImageVector
    get() {
        if (_syncthing != null) return _syncthing!!
        _syncthing = ImageVector.Builder(
            name = "Syncthing",
            defaultWidth = 24.0f.dp,
            defaultHeight = 24.0f.dp,
            viewportWidth = 118.0f,
            viewportHeight = 118.0f,
        ).apply {
            group(
                scaleX = 0.744142f,
                scaleY = 0.744142f,
                translationX = 16.4936f,
                translationY = 15.4676f,
            ) {
                addPath(
                    pathData = listOf(
                        PathNode.MoveTo(102.4f, 58.5f),
                        PathNode.ArcTo(43.7f, 43.7f, 0.0f, true, true, 15.0f, 58.5f),
                        PathNode.ArcTo(43.7f, 43.7f, 0.0f, true, true, 102.4f, 58.5f),
                        PathNode.Close,
                    ),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 6.0f,
                )
                addPath(
                    pathData = listOf(
                        PathNode.MoveTo(94.7f, 47.8f),
                        PathNode.CurveTo(99.4f, 49.4f, 104.5f, 46.9f, 106.1f, 42.2f),
                        PathNode.CurveTo(107.7f, 37.5f, 105.2f, 32.4f, 100.5f, 30.8f),
                        PathNode.CurveTo(95.8f, 29.2f, 90.7f, 31.7f, 89.1f, 36.4f),
                        PathNode.CurveTo(87.5f, 41.1f, 90.0f, 46.2f, 94.7f, 47.8f),
                        PathNode.Close,
                    ),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
                addPath(
                    pathData = listOf(
                        PathNode.MoveTo(97.6f, 39.4f),
                        PathNode.LineTo(67.5f, 64.4f),
                    ),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 6.0f,
                )
                addPath(
                    pathData = listOf(
                        PathNode.MoveTo(77.6f, 91.0f),
                        PathNode.CurveTo(77.2f, 95.9f, 80.8f, 100.3f, 85.8f, 100.8f),
                        PathNode.CurveTo(90.8f, 101.2f, 95.1f, 97.6f, 95.6f, 92.6f),
                        PathNode.CurveTo(96.0f, 87.7f, 92.4f, 83.3f, 87.4f, 82.8f),
                        PathNode.CurveTo(82.4f, 82.4f, 78.0f, 86.0f, 77.6f, 91.0f),
                        PathNode.Close,
                    ),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
                addPath(
                    pathData = listOf(
                        PathNode.MoveTo(86.5f, 91.8f),
                        PathNode.LineTo(67.5f, 64.4f),
                    ),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 6.0f,
                )
                addPath(
                    pathData = listOf(
                        PathNode.MoveTo(60.0f, 69.3f),
                        PathNode.CurveTo(62.7f, 73.5f, 68.3f, 74.7f, 72.4f, 72.0f),
                        PathNode.CurveTo(76.6f, 69.3f, 77.8f, 63.7f, 75.1f, 59.6f),
                        PathNode.CurveTo(72.4f, 55.4f, 66.8f, 54.2f, 62.7f, 56.9f),
                        PathNode.CurveTo(58.5f, 59.5f, 57.3f, 65.1f, 60.0f, 69.3f),
                        PathNode.Close,
                    ),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
                addPath(
                    pathData = listOf(
                        PathNode.MoveTo(21.2f, 61.4f),
                        PathNode.CurveTo(16.9f, 58.9f, 11.4f, 60.3f, 8.9f, 64.5f),
                        PathNode.CurveTo(6.4f, 68.8f, 7.8f, 74.3f, 12.0f, 76.8f),
                        PathNode.CurveTo(16.3f, 79.3f, 21.8f, 77.9f, 24.3f, 73.7f),
                        PathNode.CurveTo(26.8f, 69.5f, 25.4f, 64.0f, 21.2f, 61.4f),
                        PathNode.Close,
                    ),
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                )
                addPath(
                    pathData = listOf(
                        PathNode.MoveTo(16.6f, 69.1f),
                        PathNode.LineTo(67.5f, 64.4f),
                    ),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 6.0f,
                )
            }
        }.build()
        return _syncthing!!
    }

private var _syncthing: ImageVector? = null

object AppIcons
