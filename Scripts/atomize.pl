#!/usr/bin/env perl

use strict;
use warnings;
use utf8;

use Unicode::Normalize;

# field delimiter
my $FIELD_DELIM = "\t";

# list delimiter
my $LIST_DELIM = ",";

# check arguments
die "USAGE: $0 <input file> <input column> <output file> <output column>\n" unless $#ARGV == 3;
die "ERROR: input file \"$ARGV[0]\" does not exist\n" unless -f $ARGV[0];

# open file handles
open(my $input, '<:utf8', $ARGV[0]) or die "ERROR: cannot read file \"$ARGV[0]\"\n$!";
open(my $output, '>:utf8', $ARGV[2]) or die "ERROR: cannot write file \"$ARGV[2]\"\n$!";

# index of input column
my $index_input = undef;

# read input file line by line
while (<$input>) {
    my $line = $_;
    chomp($line);
    my @fields = split(/$FIELD_DELIM/, $line);

    # check if index of input column is defined
    unless (defined($index_input)) {

        # find index of input column
        for (my $index = 0; $index <= $#fields; $index++) {
            $index_input = $index if $fields[$index] eq $ARGV[1];
        }

        # die if input column is not found
        die "ERROR: input column \"$ARGV[1]\" does not exist\n" unless defined($index_input);

        # output header line
        print $output $ARGV[3] . $FIELD_DELIM . $line . "\n";
        next;
    }

    # output list of atoms at the beginning of the line
    my @atoms = @{atomize($fields[$index_input])};
    print $output join($LIST_DELIM, @atoms) . $FIELD_DELIM . $line . "\n";
}

# close file handles
close($input);
close($output);

# atomize access points
sub atomize {
    my $text = shift;

    # perform unicode normalization
    $text = NFKC($text);

    # remove itemizations and normalize punctuation
    remove_itemization($text);
    normalize_punctuation($text);

    # split text into access points
    my $access_points = [ $text ];
    $access_points = split_compound($access_points);
    $access_points = split_nested($access_points);
    $access_points = split_lists($access_points);

    # cleanup access points
    foreach (@{$access_points}) {
        cleanup($_);
    }

    # return only non-empty atomic access points
    my $atoms = [];
    foreach (@{$access_points}) {
        push($atoms, $_) unless $_ eq '';
    }

    return $atoms;
}

# remove starting text which indicates some sort of itemization
sub remove_itemization {

    # itemizations where the item is enclosed in brackets
    $_[0] =~ s/^(?:\d?-)?(?:\d+[a-zA-Z]?|[a-zA-Z])\.?\d*\s?\((.*)\)?$/$1/g;

    # itemizations that end with a space
    $_[0] =~ s/^(?:[a-zA-Z]|\d?-\d+|\d\.\d+\.?)\)?\s//g;

    # itemizations with a dot unless the item is a military unit
    $_[0] =~ s/^\d+\.\s//g unless $_[0] =~ m/armee|division/i;
}

# normalize punctuation
sub normalize_punctuation {

    # remove useless punctuation anywhere
    $_[0] =~ s/<>|{[^}]*}|\[\??\]|\*|"|_//g;

    # normalize dashes
    $_[0] =~ s/–/-/g;

    # remove dot before a dash
    $_[0] =~ s/\.-/-/g;

    # normalize date intervals
    $_[0] =~ s/(\d)\s*-\s*(\d)/$1-$2/g;

    # normalize date format with slashes
    $_[0] =~ s/(\d{2})\/(\d{2})\/(\d{4})/$1.$2.$3/g;

    # add spaces around slashes between letters
    $_[0] =~ s/([^\W\d])\s*\/\s*([^\W\d])/$1 \/ $2/g;
}

# split compound access points
sub split_compound {
    my $result = [];

    # split each compound access point in input
    foreach (@{$_[0]}) {
        push($result, split(/\s*(\s-\s|-->?|\s>>\s)\s*/));
    }

    return $result;
}

# extract nested access points
sub split_nested {
    my $result = [];

    # iterate through input access points
    foreach (@{$_[0]}) {

        # opening and closing bracket with anything but bracket in between
        my $regex_nested = qr/\([^\(\)\[]*\)|\[[^\(\[\]]*\]/;

        # extract innermost nested access points from input
        my @nested = $_ =~ m/$regex_nested/g;
        push($result, @nested);
        $_ =~ s/$regex_nested//g;

        # extract remaining nested access points recursively
        if (/$regex_nested/) {
            my $deeper_nested = [ $_ ];
            $deeper_nested = split_nested($deeper_nested);
            push($result, @{$deeper_nested});
        } else {
            push($result, $_);
        }
    }

    return $result;
}

# split items in lists
sub split_lists {
    my $result = [];

    # trim leading or trailing brackets and split
    foreach (@{$_[0]}) {
        $_ =~ s/^[\(\)\[\]]+|[\(\)\[\]]+$//g;
        push($result, split(/[\(\)\[\],;:=]+|\s[\/\.]\s/));
    }

    return $result;
}

# clean up access point
sub cleanup {

    # squash repeated spaces
    $_[0] =~ s/\s+/ /g;

    # trim leading and trailing spaces
    $_[0] =~ s/^\s|\s$//g;

    # remove dot at the end unless preceeded by a capital letter
    $_[0] =~ s/([^A-Z])\.$/$1/g;

    # trim leading and trailing punctuation
    $_[0] =~ s/^[\?-]\s*|\s*[\?-]$//g;

    # remove any leftovers
    $_[0] =~ s/^\W+$//g;
}

