#!/usr/bin/env perl

use strict;
use warnings;

die "USAGE: $0 <input file> <output file>\n" unless $#ARGV == 1;

open(my $input, '<', $ARGV[0]) or die "ERROR: cannot read from \"$ARGV[0]\"\n";
open(my $output, '>', $ARGV[1]) or die "ERROR: cannot write to \"$ARGV[1]\"\n";

while (<$input>) {
    $_ =~ s/<Klass.*?((_Titel)?>)/<collection$1/g;
    $_ =~ s/<\/Klass.*?((_Titel)?>)/<\/collection$1/g;
    $_ =~ s/<Bestand.*?>/<Bestand>/g;
    $_ =~ s/<\/Bestand.*?>/<\/Bestand>/g;

    print $output $_;
}

close($output);
close($input);

