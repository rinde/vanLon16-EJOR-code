#!/bin/bash

# Converts a point file to a vector image using GNUPLOT

create_plot(){
filename=$1

gnuplot << EOF
	set terminal postscript enh eps color
	set title 'Locations generated from $filename'
	set output '$filename.eps'
	set key off
	set xlabel 'x (km)'
	set ylabel 'y (km)'
	set size square
	plot '$filename'
EOF

temp="-tmp.pdf"
eps=".eps"
pdf=".pdf"
ps2pdf -dEmbedAllFonts=true -dEPSCrop $filename$eps $filename$temp

gs -q -dNOPAUSE -dBATCH -sDEVICE=pdfwrite -sOutputFile=$filename$pdf $filename$temp

rm $filename$eps
rm $filename$temp
}



for f in *.points
do
	echo $f
	create_plot $f
done


