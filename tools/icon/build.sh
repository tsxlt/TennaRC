set -e
N=648; u=6   # 108 units at 6px
px() { echo $(( $1 * u )); }
magick -size ${N}x${N} xc: -sparse-color Shepards "0,0 #FFBE74  324,0 #FF8A4C  648,0 #FF6A48  0,324 #FF7A3E  324,324 #FF6240  648,324 #E8466A  0,648 #F0503C  324,648 #D93E62  648,648 #A8327E" bg0.png
magick bg0.png \( -size ${N}x${N} radial-gradient:'rgba(255,255,255,0.32)'-'rgba(255,255,255,0)' -geometry -120-220 \) -composite -alpha off bg1.png
magick -size ${N}x${N} xc:gray50 -seed 7 -attenuate 0.9 +noise Gaussian grain_c.png
magick -size ${N}x${N} xc:gray50 -seed 11 -attenuate 0.7 +noise Gaussian -colorspace gray grain_v.png
grain() { # in colour% value% out
  magick "$1" \( grain_c.png -alpha set -channel A -evaluate set $2% +channel \) -compose overlay -composite \( grain_v.png -alpha set -channel A -evaluate set $3% +channel \) -compose overlay -composite -alpha off "$4"
}
grain bg1.png 10 14 bg.png
TV="roundrectangle $(px 26),$(px 32) $(px 72),$(px 62) $(px 7),$(px 7)"
RM="roundrectangle $(px 59),$(px 44) $(px 77),$(px 84) $(px 9),$(px 9)"
mask() { magick -size ${N}x${N} xc:black -fill white -draw "$1" "$2"; }
mask "$TV" m_tv.png
mask "$RM" m_rm.png
frost() { # under mask lift out
  magick "$1" -blur 0x16 -fill white -colorize $3 tmp_lift.png
  grain tmp_lift.png 14 30 tmp_grain.png
  magick tmp_grain.png "$2" -alpha off -compose CopyOpacity -composite "$4"
}
shadow() { # mask out
  magick "$1" -background none -fill '#5A0F2A' -colorize 100 \( "$1" \) -alpha off -compose CopyOpacity -composite -channel A -evaluate multiply 0.26 +channel -blur 0x16 -page +0+18 -background none -flatten "$2"
}
edge() { # draw out  (a light rim, brightest top left)
  magick -size ${N}x${N} xc:none -fill none -stroke white -strokewidth 4 -draw "$1" \( -size ${N}x${N} gradient:'rgba(255,255,255,1)'-'rgba(255,255,255,0.25)' -rotate -45 -gravity center -crop ${N}x${N}+0+0 +repage \) -compose DstIn -composite -channel A -evaluate multiply 0.8 +channel "$2"
}
shadow m_tv.png sh_tv.png
shadow m_rm.png sh_rm.png
frost bg.png m_tv.png 26 fr_tv.png
edge "$TV" ed_tv.png
magick bg.png sh_tv.png -composite fr_tv.png -composite ed_tv.png -composite -alpha off s1.png
magick s1.png sh_rm.png -composite s1b.png
frost s1b.png m_rm.png 44 fr_rm.png
edge "$RM" ed_rm.png
magick -size ${N}x${N} xc:none -fill none -stroke 'rgba(255,255,255,0.85)' -strokewidth 9 -draw "circle $(px 68),$(px 55) $(px 68),$(( 55*u - 34 ))" -stroke none -fill '#FF7A35' -draw "circle $(px 68),$(px 55) $(px 68),$(( 55*u - 12 ))" -fill 'rgba(255,255,255,0.7)' -draw "roundrectangle $(( 68*u - 11 )),$(px 67) $(( 68*u + 11 )),$(px 78) 11,11" keys.png
magick s1b.png fr_rm.png -composite ed_rm.png -composite keys.png -composite -alpha off full.png
magick full.png -crop 432x432+108+108 +repage visible.png
magick -size 432x432 xc:black -fill white -draw "roundrectangle 0,0 431,431 110,110" -alpha off msq.png
magick -size 432x432 xc:black -fill white -draw "circle 216,216 216,0" -alpha off mc.png
magick visible.png -alpha set msq.png -compose CopyOpacity -composite v_sq.png
magick visible.png -alpha set mc.png -compose CopyOpacity -composite v_c.png
magick v_sq.png v_c.png -background "#ECEDEF" -splice 16x0 +append \( -size 260x432 xc:"#ECEDEF" \( v_sq.png -resize 120x120 \) -gravity center -composite \) +append view.png
