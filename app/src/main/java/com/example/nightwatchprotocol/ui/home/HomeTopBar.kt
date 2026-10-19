package com.example.nightwatchprotocol.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.theme.NightWatchBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily

private val TopBarTextColor = Color(0xFFDDDDDD)

/**
 * Logo, [title] and the white line of the tabs of Home (Figma 49:15 and 2266:72), below the status bar. The title is
 * 105 dp wide, so "Ruta de Aprendizaje" takes two lines as in the design. [trailing] is the icon on the right, if any
 * (Inicio has the exit icon). [titleTop] is 14 dp for two-line titles and 25 dp for the one-line "Laboratorios" (2271:86). Same layout as `ProfileTopBar`, which has an "X" instead.
 */
@Composable
internal fun HomeTopBar(
    title: String,
    modifier: Modifier = Modifier,
    titleTop: Dp = 14.dp,
    trailing: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth().statusBarsPadding().background(NightWatchBackground)) {
        Box(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Image(
                painter = painterResource(id = R.drawable.img_logo_nightwatch),
                contentDescription = null,
                modifier = Modifier.padding(start = 11.dp, top = 9.dp).size(50.dp),
            )
            Text(
                text = title,
                color = TopBarTextColor,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 66.dp, top = titleTop).width(105.dp),
            )
            trailing?.invoke(this)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White),
        )
    }
}
